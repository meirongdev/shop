---
title: 平台扩展服务
---

# 平台扩展服务

本分组提供横向平台能力，为交易与增长服务提供扩展支撑，同时也是最适合承接“新能力试点”的区域。

## 服务清单

| 服务 | 主职责 | 主技术栈 | 可扩展点 |
|---|---|---|---|
| `activity-service` | 互动活动引擎、反作弊、奖励派发编排 | Spring Boot、JPA、Flyway、MySQL、Redis、Kafka | `GamePlugin`、`AntiCheatGuard`、奖励派发 |
| `search-service` | 检索、联想词、热词、索引重建 | Spring Boot、Meilisearch、Kafka、OpenFeature | 索引配置、`ProductSearchService`、`shop.features.*` |
| `webhook-service` | 对外事件推送、签名、失败重试 | Spring Boot、JPA、Flyway、MySQL、Kafka | `WebhookDeliveryService`、`WebhookSigner`、退避策略 |
| `subscription-service` | 订阅计划、生命周期、续费任务 | Spring Boot、JPA、Flyway、MySQL、Kafka、Redisson | 计划模板、状态流转、续费调度参数 |

## activity-service

关键 API：

- `GET /activity/v1/games`
- `GET /activity/v1/games/{id}/info`
- `POST /activity/v1/games/{id}/participate`
- `GET /activity/v1/games/{id}/my-history`

扩展思路：

- 新玩法优先实现 `GamePlugin`，由 `GamePluginRegistry` 自动发现。
- 反作弊规则和奖励派发编排也已经被单独抽出，不需要复制整套游戏服务。

## search-service

关键 API：

- `GET /search/v1/products`
- `GET /search/v1/products/suggestions`
- `GET /search/v1/queries/trending`
- `POST /search/v1/products/_reindex`

扩展思路：

- 搜索能力通过 `ProductSearchService` 封装 SDK，适合继续试验索引规则、分词、排序和引擎替换。
- `shop.features.*` 让 autocomplete / trending / locale-aware 这类能力可以渐进发布。

## webhook-service

关键 API：

- `POST /webhook/v1/endpoint/create`
- `POST /webhook/v1/endpoint/list`
- `POST /webhook/v1/delivery/list`

扩展思路：

- 回调投递、退避和超时集中在 `WebhookDeliveryService`。
- 签名算法目前集中在 `WebhookSigner`，若将来需要升级算法，可从这里统一替换。

## subscription-service

关键能力：

- 订阅计划管理。
- 用户订阅生命周期状态管理。
- 定时续费与续费日志。

扩展思路：

- 新订阅计划、新续费窗口、新 grace period 都应继续落在订阅域模型和 `SubscriptionRenewalService` 中。
- Redisson 已用于多实例续费任务抢占，避免重复扫描和重复扣费。
