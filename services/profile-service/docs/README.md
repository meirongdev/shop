---
title: 用户档案服务设计文档
---

# profile-service — 用户档案服务设计文档

> 版本：1.0 | 日期：2026-03-20

---

## 一、服务定位

```
profile-service（:8083）

职责：
  买家档案：基本信息（昵称、头像、邮箱、手机）
  卖家档案：店铺信息、营业信息
  地址簿：管理多个收货地址
  收藏夹：收藏商品 / 店铺
  到货提醒订阅

不归属此服务：
  ❌ 会员等级（tier） → loyalty-service 根据 tier_points 维护
  ❌ 账号认证 / 密码 → auth-server
  ❌ 积分 → loyalty-service
```

---

## 二、数据库设计（shop_profile）

### 2.1 买家档案（现有 + 扩展）

```sql
-- 现有表扩展
ALTER TABLE buyer_profile
  ADD COLUMN avatar_url    VARCHAR(512),
  ADD COLUMN phone         VARCHAR(32),
  ADD COLUMN gender        VARCHAR(10),         -- MALE/FEMALE/PREFER_NOT_TO_SAY
  ADD COLUMN birthday      DATE,
  ADD COLUMN locale        VARCHAR(10)  NOT NULL DEFAULT 'en-US',
  ADD COLUMN timezone      VARCHAR(64)  NOT NULL DEFAULT 'UTC',
  ADD COLUMN is_active     TINYINT(1)   NOT NULL DEFAULT 1,
  ADD COLUMN last_login_at TIMESTAMP(6),
  ADD COLUMN created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                            ON UPDATE CURRENT_TIMESTAMP(6);

-- 现有字段：buyer_id, username, display_name, email, tier
-- tier 字段逐步迁移到 loyalty-service 管理，profile-service 保留冗余副本（读取用）
```

### 2.2 卖家档案（新增）

```sql
CREATE TABLE seller_profile (
    seller_id        VARCHAR(64)    NOT NULL PRIMARY KEY,
    username         VARCHAR(64)    NOT NULL UNIQUE,
    shop_name        VARCHAR(128)   NOT NULL,
    shop_description TEXT,
    shop_logo_url    VARCHAR(512),
    shop_banner_url  VARCHAR(512),
    email            VARCHAR(256)   NOT NULL,
    phone            VARCHAR(32),
    business_type    VARCHAR(32)    NOT NULL DEFAULT 'INDIVIDUAL',  -- INDIVIDUAL/COMPANY
    verified         TINYINT(1)     NOT NULL DEFAULT 0,
    rating_avg       DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
    rating_count     INT            NOT NULL DEFAULT 0,
    total_sales      BIGINT         NOT NULL DEFAULT 0,
    is_active        TINYINT(1)     NOT NULL DEFAULT 1,
    created_at       TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
```

### 2.3 地址簿

```sql
CREATE TABLE buyer_address (
    id           VARCHAR(36)   NOT NULL PRIMARY KEY,
    buyer_id    VARCHAR(64)   NOT NULL,
    label        VARCHAR(32)   NOT NULL DEFAULT 'home',   -- home/office/other
    name         VARCHAR(128)  NOT NULL,
    phone        VARCHAR(32)   NOT NULL,
    line1        VARCHAR(256)  NOT NULL,
    line2        VARCHAR(256),
    city         VARCHAR(64)   NOT NULL,
    state        VARCHAR(64),
    postal_code  VARCHAR(16)   NOT NULL,
    country      VARCHAR(2)    NOT NULL DEFAULT 'US',     -- ISO 3166-1 alpha-2
    is_default   TINYINT(1)    NOT NULL DEFAULT 0,
    created_at   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_player (buyer_id)
);
```

### 2.4 商品收藏

```sql
CREATE TABLE buyer_favorite_product (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    buyer_id   VARCHAR(64)   NOT NULL,
    product_id  VARCHAR(36)   NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_player_product (buyer_id, product_id)
);
```

### 2.5 到货提醒

```sql
CREATE TABLE buyer_restock_notify (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    buyer_id   VARCHAR(64)   NOT NULL,
    sku_id      VARCHAR(36)   NOT NULL,
    notified    TINYINT(1)    NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_player_sku (buyer_id, sku_id)
);
```

---

## 三、API 设计

```
# 买家（JWT 鉴权）
GET  /profile/v1/buyer                        # 获取我的档案
PUT  /profile/v1/buyer                        # 更新档案（昵称、头像、生日等）
GET  /profile/v1/buyer/addresses              # 地址列表
POST /profile/v1/buyer/addresses              # 新增地址
PUT  /profile/v1/buyer/addresses/{id}         # 更新地址
DELETE /profile/v1/buyer/addresses/{id}       # 删除地址
PUT  /profile/v1/buyer/addresses/{id}/default  # 设为默认地址

GET  /profile/v1/buyer/favorites              # 收藏商品列表
POST /profile/v1/buyer/favorites              # 收藏商品
DELETE /profile/v1/buyer/favorites/{productId}  # 取消收藏

# 卖家（JWT 鉴权）
GET  /profile/v1/seller                       # 获取店铺信息
PUT  /profile/v1/seller                       # 更新店铺信息

# 公开（无需 JWT）
GET  /public/sellers/{sellerId}               # 公开店铺主页

# 内部接口 (Service-to-service security is enforced via Kubernetes NetworkPolicy (Cilium). Identity context is propagated via trusted headers injected by the Gateway.)
GET  /internal/profile/buyer/{buyerId}       # BFF 聚合查询
GET  /internal/profile/seller/{sellerId}      # BFF 聚合查询
PUT  /internal/profile/buyer/{buyerId}/tier  # loyalty-service 同步会员等级
POST /internal/profile/buyer/{buyerId}/notify-restock  # 到货后触发提醒
```

---

## 四、与 loyalty-service 的会员等级同步

```
loyalty-service 维护权威 tier_points，当等级变化时：
  Kafka: loyalty.tier.upgraded.v1 {buyerId, newTier}
    → profile-service 消费 → 更新 buyer_profile.tier

buyer-bff Dashboard 读取：
  profile.tier（来自 profile-service，带缓存）
  loyalty.balance（来自 loyalty-service，实时）
```

---

## 五、生日礼营销联动

```
promotion-service 中 BIRTHDAY 类型活动条件：
  评估时调用 profile-service：
    GET /internal/profile/buyer/{buyerId}
    → 判断 birthday 月份 = 当前月份

或（推荐）：定时任务每天检查当日生日用户，主动发放生日券：
  PromotionScheduler.issueBirthdayGifts()
    → SELECT buyer_id FROM buyer_profile WHERE MONTH(birthday) = ? AND DAY(birthday) = ?
    → 批量调用 promotion-service 发放 BIRTHDAY 类型优惠券
```
