---
title: 商品服务设计文档
---

# marketplace-service — 商品服务设计文档

> 版本：1.0 | 日期：2026-03-20 | 基于现有代码扩展设计

---

## 一、服务定位

```
marketplace-service（:8084）

职责：
  1. 商品目录管理   — CRUD、图片、SKU
  2. 商品搜索       — 关键词、分类、筛选、排序
  3. 库存管理       — 库存查询、锁定、释放
  4. 商品分类       — 分类树管理
  5. 商品状态管理   — 草稿/审核中/已上架/已下架
```

---

## 二、数据库设计（shop_marketplace）

### 2.1 现有表（扩展）

```sql
-- 现有 marketplace_product 表（保留，新增字段）
ALTER TABLE marketplace_product
  ADD COLUMN category_id     VARCHAR(36)    AFTER seller_id,
  ADD COLUMN description     TEXT,
  ADD COLUMN images          JSON,           -- ["url1","url2",...] 图片列表
  ADD COLUMN tags            JSON,           -- ["summer","trending"]
  ADD COLUMN status          VARCHAR(32)    NOT NULL DEFAULT 'PUBLISHED',
                                              -- DRAFT/PENDING_REVIEW/PUBLISHED/UNPUBLISHED
  ADD COLUMN video_url       VARCHAR(512),
  ADD COLUMN weight_gram     INT,
  ADD COLUMN attributes      JSON,           -- {"color":"red","size":"M"}
  ADD COLUMN sales_count     INT            NOT NULL DEFAULT 0,
  ADD COLUMN rating_avg      DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
  ADD COLUMN rating_count    INT            NOT NULL DEFAULT 0,
  ADD COLUMN created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                              ON UPDATE CURRENT_TIMESTAMP(6);
```

### 2.2 商品分类表

```sql
CREATE TABLE product_category (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    parent_id   VARCHAR(36),                           -- NULL = 顶级分类
    name        VARCHAR(64)   NOT NULL,
    slug        VARCHAR(64)   NOT NULL UNIQUE,          -- URL 友好名称
    icon_url    VARCHAR(256),
    sort_order  INT           NOT NULL DEFAULT 0,
    active      TINYINT(1)    NOT NULL DEFAULT 1,
    FOREIGN KEY (parent_id) REFERENCES product_category(id)
);

-- 初始化分类
INSERT INTO product_category VALUES
('cat-electronics', NULL, '电子产品', 'electronics', '/icons/electronics.svg', 1, 1),
('cat-clothing',    NULL, '服装',     'clothing',     '/icons/clothing.svg',    2, 1),
('cat-beauty',      NULL, '美妆',     'beauty',       '/icons/beauty.svg',      3, 1),
('cat-home',        NULL, '家居',     'home',         '/icons/home.svg',        4, 1),
('cat-phone',       'cat-electronics', '手机', 'phones', NULL, 1, 1),
('cat-laptop',      'cat-electronics', '笔记本', 'laptops', NULL, 2, 1);
```

### 2.3 SKU 多规格表

```sql
CREATE TABLE product_sku (
    id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    product_id      VARCHAR(36)    NOT NULL,
    sku_code        VARCHAR(64)    NOT NULL UNIQUE,     -- 商家自定义 SKU 码
    attributes      JSON           NOT NULL,            -- {"color":"red","size":"M"}
    price           DECIMAL(19,2)  NOT NULL,
    compare_price   DECIMAL(19,2),                      -- 划线价
    stock           INT            NOT NULL DEFAULT 0,
    reserved_stock  INT            NOT NULL DEFAULT 0,  -- 已锁定（未付款）
    image_url       VARCHAR(512),
    active          TINYINT(1)     NOT NULL DEFAULT 1,
    FOREIGN KEY (product_id) REFERENCES marketplace_product(id)
);
```

### 2.4 库存操作日志

```sql
CREATE TABLE inventory_log (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku_id      VARCHAR(36)   NOT NULL,
    type        VARCHAR(32)   NOT NULL,    -- LOCK / RELEASE / DEDUCT / ADJUST / RESTOCK
    delta       INT           NOT NULL,    -- 正数增加，负数减少
    reason      VARCHAR(128),
    reference_id VARCHAR(64),              -- 关联 order_id
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
```

---

## 三、库存状态机

```
stock（总库存）= 可用库存 + 已锁定库存 + 已售出

操作时序：
  1. 加购 / 下单预占   → reserved_stock += N         (LOCK)
  2. 支付成功          → stock -= N, reserved_stock -= N  (DEDUCT)
  3. 订单取消 / 超时   → reserved_stock -= N         (RELEASE)
  4. 商家补货          → stock += N                  (RESTOCK)

可用库存 = stock - reserved_stock
```

```java
@Transactional
public void lockInventory(List<LockItem> items) {
    for (LockItem item : items) {
        ProductSkuEntity sku = skuRepository.findByIdForUpdate(item.skuId());  // SELECT FOR UPDATE
        int available = sku.getStock() - sku.getReservedStock();
        if (available < item.quantity()) {
            throw new BusinessException(INSUFFICIENT_INVENTORY,
                "SKU %s: need %d, available %d".formatted(item.skuId(), item.quantity(), available));
        }
        sku.setReservedStock(sku.getReservedStock() + item.quantity());
        skuRepository.save(sku);
        inventoryLogRepository.save(new InventoryLog(sku.getId(), "LOCK", -item.quantity(), item.orderId()));
    }
}
```

### 3.1 多实例库存协调（2026-03-23）

当前实际实现没有单独拆出库存预占服务，而是在 `MarketplaceApplicationService` 内对库存写路径增加 Redisson 分布式锁：

- `deductInventory`
- `restoreInventory`

锁键格式：

```text
shop:marketplace:inventory:mutate:{productId}
```

这样做的原因是：

- 同一商品可能被多个 buyer 并发下单
- 订单取消/补偿也会回写同一库存字段
- 单纯依赖“先读再改再写”的数据库事务，在多实例下仍会面临竞争窗口

当前策略是先获取 Redisson `RLock`，再执行数据库事务内的库存读改写。  
如果锁已被其它实例持有，则显式返回“库存繁忙，请重试”，而不是静默吞掉冲突。

---

## 四、商品状态流转

```
DRAFT          ← 卖家创建，未提交
   │
   │ [提交审核]
   ▼
PENDING_REVIEW ← 等待平台审核
   │            │
   │ [审核通过] │ [审核拒绝]
   ▼            ▼
PUBLISHED    REJECTED（含拒绝原因）→ 卖家修改 → 再次提交
   │
   │ [卖家下架 / 平台强制下架]
   ▼
UNPUBLISHED  → 可重新上架（无需审核）
```

---

## 五、搜索设计

### Phase 1：MySQL 全文搜索（当前可用）

```sql
-- 添加全文索引
ALTER TABLE marketplace_product
  ADD FULLTEXT INDEX ft_name_desc (name, description);

-- 搜索查询
SELECT p.*, ps.price AS min_price
FROM marketplace_product p
JOIN product_sku ps ON ps.product_id = p.id
WHERE p.status = 'PUBLISHED'
  AND MATCH(p.name, p.description) AGAINST (? IN BOOLEAN MODE)
  AND p.category_id = ?          -- 分类筛选
  AND ps.price BETWEEN ? AND ?   -- 价格筛选
ORDER BY CASE ?
  WHEN 'SALES'  THEN p.sales_count
  WHEN 'NEWEST' THEN p.created_at
  ELSE p.rating_avg
END DESC
LIMIT ? OFFSET ?;
```

### Phase 4：Meilisearch 搜索能力增强

search-service 已实现基础搜索（全文检索、分类筛选、价格排序、分页）。Phase 4 升级方向：

```yaml
# Meilisearch index settings 增强
products:
  searchableAttributes: [name, description, category_name, seller_name, tags]
  filterableAttributes: [category_id, seller_id, min_price, status]
  sortableAttributes: [min_price, sales_count, rating_avg, published_at]
  # Phase 4：向量搜索（语义相似）
  embedders:
    default:
      source: huggingFace
      model: sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

---

## 六、API 设计

```
# 公开接口（无需 JWT，游客可访问）
GET  /public/products                  # 商品列表（分页+筛选+排序）
GET  /public/products/{id}             # 商品详情（含 SKU 列表）
GET  /public/products/search?q=        # 搜索
GET  /public/categories                # 分类树
GET  /public/categories/{slug}         # 分类下的商品

# 买家接口（需 JWT）
POST /marketplace/v1/products/{id}/favorites   # 收藏商品
DELETE /marketplace/v1/products/{id}/favorites  # 取消收藏
GET  /marketplace/v1/favorites                  # 收藏列表
POST /marketplace/v1/products/{id}/notify       # 到货提醒（订阅缺货商品）

# 卖家接口（通过 seller-bff）
GET  /marketplace/v1/seller/products           # 我的商品
POST /marketplace/v1/seller/products           # 创建商品（草稿）
PUT  /marketplace/v1/seller/products/{id}      # 更新商品
POST /marketplace/v1/seller/products/{id}/submit    # 提交审核
POST /marketplace/v1/seller/products/{id}/unpublish  # 下架
POST /marketplace/v1/seller/products/{id}/publish    # 重新上架
POST /marketplace/v1/seller/products/{id}/images     # 上传图片

# 内部接口（X-Internal-Token）
POST /internal/marketplace/inventory/lock      # order-service 锁定库存
POST /internal/marketplace/inventory/release   # order-service 释放库存
POST /internal/marketplace/inventory/deduct    # order-service 正式扣减
GET  /internal/marketplace/products/{id}       # BFF 聚合查询
POST /internal/marketplace/products/{id}/review  # 平台审核（Admin）
```

---

## 七、缓存策略（Redis）

```
商品详情:     cache:product:{id}           TTL = 10min  Cache-Aside
商品列表:     cache:products:{hash(query)} TTL = 2min   Cache-Aside
分类树:       cache:categories             TTL = 1h     定时刷新
热销商品:     cache:hot_products           TTL = 5min   定时排行
库存（热商品）:cache:stock:{sku_id}        TTL = 30s    Write-Through（同步到 DB）

缓存失效：
  商品更新时 → 删除 cache:product:{id}
  库存变化时 → 更新 cache:stock:{sku_id}（Write-Through）
```

---

## 八、Kafka 事件

| Topic 发布 | 触发时机 | 消费方 |
|-----------|---------|--------|
| `inventory.events.v1 (LOW_STOCK)` | 库存 ≤ 预警阈值 | notification-service（通知卖家补货）|
| `inventory.events.v1 (RESTOCKED)` | 商品到货 | loyalty-service（触发到货提醒通知）|
| `marketplace.product.published.v1` | 商品上架审核通过 | 搜索索引更新 |
| `marketplace.product.unpublished.v1` | 商品下架 | 搜索索引更新、购物车标记失效 |

| Topic 消费 | 来源 | 处理 |
|-----------|------|------|
| `order.completed.v1` | order-service | 更新商品 sales_count |
| `order.cancelled.v1` | order-service | 释放锁定库存 |
