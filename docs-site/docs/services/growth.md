---
title: 用户增长服务
---

# 用户增长服务

本分组聚焦留存、转化与复购，不直接承担交易主链路事实源。

## 服务清单

| 服务 | 端口 | 职责 |
|---|---:|---|
| Loyalty Service | 8088 | 积分账户、签到、兑换、新手任务 |
| Promotion Service | 8087 | 促销引擎、优惠券、结算折扣 |
| Notification Service | 8092 | 事件通知、模板渲染、幂等重试 |

## Loyalty Service

关键 API：

- `GET /loyalty/v1/account`
- `POST /loyalty/v1/checkin`
- `GET /loyalty/v1/rewards`
- `POST /loyalty/v1/rewards/redeem`
- `GET /loyalty/v1/onboarding/tasks`

说明：已支持积分到期批处理与新手任务体系；`user.registered.v1` / `order.events.v1` 消费链路现在会把契约错误直接送入 DLT，把数据库或 `profile-service` 的瞬时失败交给 Kafka 做有限重试。

## Promotion Service

关键 API：

- `POST /promotion/v1/offer/create`
- `POST /promotion/v1/coupon/validate`
- `POST /promotion/v1/coupon/apply`
- `POST /promotion/v1/calculate`

说明：当前处于 legacy `coupon` 与 `coupon_template/coupon_instance` 双轨过渡态；其中模板发券链路已通过 Redisson 分布式锁串行化模板级配额校验，避免多实例并发超发；欢迎券/钱包奖励 listener 现在会把 poison pill 直接送入 DLT，仅对数据库瞬时故障做有限重试。

## Notification Service

关键能力：

- 监听 `user.registered.v1` / `order.events.v1` / `wallet.transactions.v1`
- 按模板发送邮件（本地 Mailpit 可观测）
- `(event_id, channel)` 幂等 + 失败重试

说明：通知是典型异步消费能力，与交易核心服务解耦；解析/契约错误直接进入 DLT，真实邮件投递失败则保留在 `notification_log` 并由定时任务继续补投。
