---
title: 平台扩展服务
---

# 平台扩展服务

本分组提供横向平台能力，为交易与增长服务提供扩展支撑。

## 服务清单

| 服务 | 端口 | 职责 |
|---|---:|---|
| Activity Service | 8089 | 互动活动引擎与防作弊 |
| Search Service | 8091 | 检索能力与开关控制 |
| Webhook Service | 8093 | 对外事件推送 |
| Subscription Service | 8094 | 订阅计划与续费生命周期 |

## Activity Service

关键 API：

- `GET /activity/v1/games`
- `GET /activity/v1/games/{id}/info`
- `POST /activity/v1/games/{id}/participate`
- `GET /activity/v1/games/{id}/my-history`

说明：当前已实现 4 种玩法；`RewardDispatcher` 外部奖励派发仍为桩。

## Search Service

关键 API：

- `GET /search/v1/products`
- `GET /search/v1/products/suggestions`
- `GET /search/v1/queries/trending`
- `POST /search/v1/products/_reindex`

说明：通过 OpenFeature 开关控制 autocomplete/trending/locale-aware；由 BFF 直接调用，不经 Gateway；Kafka 投影消费继续采用 `@RetryableTopic + DLT`，适合作为可重建 projection 样板。

## Webhook Service

关键 API：

- `POST /webhook/v1/endpoint/create`
- `POST /webhook/v1/endpoint/list`
- `POST /webhook/v1/delivery/list`

说明：支持 HMAC-SHA256 签名、指数退避重试；解析错误直接进入 DLT，真实回调失败保留在 `webhook_delivery` 并由定时重试接管。

## Subscription Service

关键能力：

- 订阅计划管理
- 用户订阅状态机（ACTIVE/PAUSED/CANCELLED/EXPIRED）
- 定时续费与续费日志

说明：订阅深化（自动下单、权益联动）仍属于 roadmap 后续项；当前续费批处理已通过 Redisson 分布式锁保证多实例只会有一个节点执行本轮扫描。
