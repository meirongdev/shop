---
title: BFF 模式
---

# BFF 模式

## 为什么需要 BFF

在本项目中，BFF 解决的是“前端体验与领域边界之间的错位”：

- Portal 需要一屏聚合多个领域数据
- 领域服务应保持单一职责与独立演进
- 因此由 BFF 负责聚合、编排与协议适配

## Thin BFF 原则

- BFF 负责聚合、鉴权上下文透传、请求编排
- 业务事实与状态机仍归属领域服务
- BFF 可维护少量体验态（如游客购物车），但不替代领域事实源

## Buyer / Seller 分离

- Buyer BFF：购物与用户体验聚合
- Seller BFF：经营与履约聚合

分离后可以独立优化接口形态与降级策略，避免单一超大聚合层。

## 运行时治理

- 默认 bounded timeout（connect/read）
- 非核心依赖（promotion/loyalty/search）可降级
- 核心依赖（如库存扣减）快速失败
- Gateway 注入 Trusted Headers，BFF 以可信上下文执行

## 与 Portal 的关系

Portal 使用 Kotlin + Thymeleaf SSR，但 northbound API 形态按前后端分离组织。未来迁移前端技术栈时，BFF 可保持稳定。

## 不放在本页的内容

具体 API 明细、DTO 字段与错误码不在本页展开，统一参考 `docs/services/*.md` 与 `shop-contracts`。
