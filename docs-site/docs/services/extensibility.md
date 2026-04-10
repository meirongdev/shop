---
title: 扩展能力与二次开发
---

# 扩展能力与二次开发

如果你要基于这套仓库继续长业务，最重要的不是“再建一个服务”，而是先看现有服务已经给你留了哪些扩展面。

## 先复用四类平台积木

| 积木 | 用途 | 什么时候先看它 |
|---|---|---|
| `shop-common` | 统一响应、异常、安全、可观测基线 | 新服务、新 northbound API、新错误语义 |
| `shop-contracts` | path 常量、共享 DTO、event envelope（按领域子模块拆分） | BFF / Portal / Worker 需要复用契约时 |
| `shop-archetypes` | 新服务模板 | 新建 domain service 或 event worker 时 |
| `shop.features.*` | 实验开关和渐进发布 | 新能力要灰度验证时 |

## 最重要的扩展点一览

| 服务 | 已有扩展点 | 典型二次开发场景 |
|---|---|---|
| `api-gateway` | 路由 YAML、自定义 filter、限流阈值 | 增加公开 API、灰度 header、统一审计策略 |
| `auth-server` | `SmsGateway`、`SocialLoginService`、`JwtTokenService` | 加新短信供应商、社交登录、token claims |
| `buyer-bff` / `seller-bff` | RestClient 注册、聚合服务、timeout / 降级配置 | 加新的聚合接口或新的端侧视图 |
| `marketplace-service` | outbox、库存锁、商品模型 | 加商品属性、库存同步、商品事件 |
| `order-service` | 状态机、outbox、scheduler | 加订单状态、补偿动作、批处理规则 |
| `wallet-service` | `StripeGateway` | 接更多支付提供方 |
| `promotion-service` | `BenefitCalculator`、`ConditionEvaluator` | 加新促销类型或新的条件判断 |
| `activity-service` | `GamePlugin`、`AntiCheatGuard` | 加新玩法、奖池规则、反作弊策略 |
| `notification-service` | `NotificationChannel`、`NotificationRouteConfig`、模板 | 加 SMS / IM 渠道，新增事件模板 |
| `search-service` | 搜索封装、索引配置、特性开关 | 调整排序、分词、联想词策略 |
| `webhook-service` | 投递服务、签名、退避参数 | 换签名方式、增加 callback 策略 |
| `subscription-service` | 生命周期状态、续费任务 | 加新订阅计划、续费窗口或试用态 |
| `buyer-portal` | API client、Controller、Thymeleaf | 扩页面、表单流程、模板组件 |
| KMP `seller-app` | feature 模块、`SellerApi` 契约 | 新卖家功能页面、seller-bff 新聚合接口 |

## 三种最常见的扩展路线

### 1. 新规则，而不是新服务

优先检查是否能复用：

- `BenefitCalculator` / `ConditionEvaluator`
- `GamePlugin`
- `NotificationChannel`
- `StripeGateway`

这类接口已经把变化点抽出来，新增一个实现通常比拆新服务便宜得多。

### 2. 新读模型，而不是侵入交易库

如果需求是：

- 更复杂的搜索
- 热榜 / 趋势榜
- 卖家统计看板
- 活动参与分析

优先考虑 **Kafka 投影 + 独立读模型**，而不是直接给交易表加越来越重的查询。

### 3. 新端侧体验，而不是把规则推回 BFF

如果需求是新页面、新表单、新端侧组合：

- 先看 Portal 模板与 BFF 聚合接口。
- BFF 只负责组合，不承载新的领域事实。
- 真正的状态机、余额、库存、积分、券规则继续留在领域服务。

## 新增能力时的最小 checklist

1. 是否已有现成 SPI / strategy / template 可以复用？
2. 是否需要把共享 DTO / path / event 放进 `shop-contracts`？
3. 是否需要补 metrics、trace、日志关键字段和文档？
4. 是否应该走 outbox + Kafka，而不是在事务里直接同步调用？
5. 是否需要同步更新 `docs/` 与 `docs-site/`，让下一位读者看得懂？

满足这五项，二次开发通常就不会把平台边界越做越乱。
