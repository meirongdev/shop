---
title: 用户增长服务
---

# 用户增长服务

本分组聚焦留存、转化与复购，不直接承担交易主链路事实源，但会通过规则、事件和模板持续影响用户体验。

## 服务清单

| 服务 | 主职责 | 主技术栈 | 可扩展点 |
|---|---|---|---|
| `loyalty-service` | 积分账户、签到、兑换、过期处理 | Spring Boot、JPA、Flyway、MySQL、Kafka | 积分规则、签到参数、兑换规则、批处理 |
| `promotion-service` | 促销引擎、优惠券、结算折扣 | Spring Boot、JPA、Flyway、MySQL、Kafka、Redisson | `BenefitCalculator`、`ConditionEvaluator`、coupon 模型 |
| `notification-service` | 邮件通知、模板渲染、事件监听、重试 | Spring Boot、Spring Mail、Thymeleaf、JPA、Kafka | `NotificationChannel`、`NotificationRouteConfig`、模板文件 |

## loyalty-service

关键 API：

- `GET /loyalty/v1/account`
- `POST /loyalty/v1/checkin`
- `GET /loyalty/v1/rewards`
- `POST /loyalty/v1/rewards/redeem`
- `GET /loyalty/v1/onboarding/tasks`

扩展思路：

- 签到、积分到账、积分过期都已经有独立服务与定时任务，可以继续沿事件监听和配置扩展。
- 把“积分规则”留在 loyalty 域，而不是塞回订单或 BFF。

## promotion-service

关键 API：

- `POST /promotion/v1/offer/create`
- `POST /promotion/v1/coupon/validate`
- `POST /promotion/v1/coupon/apply`
- `POST /promotion/v1/calculate`

扩展思路：

- 新增促销类型时，优先实现新的 `BenefitCalculator` / `ConditionEvaluator`，让 `PromotionEngine` 自动发现。
- 模板发券链路已通过 Redisson 协调配额，多实例场景不要绕开它直接抢券。
- 当前 legacy `coupon` 与 `coupon_template/coupon_instance` 双轨并行，扩展时要明确自己落在哪条模型线上。

## notification-service

关键能力：

- 监听订单、钱包、用户注册等 Kafka 事件。
- 用 Thymeleaf 模板渲染邮件内容。
- 对 `(event_id, channel)` 做幂等记录和失败重试。

扩展思路：

- 新渠道优先实现 `NotificationChannel`。
- 新事件到新模板的映射先收敛到 `NotificationRouteConfig`。
- 模板文案与布局优先改 `templates/email/*`，不要把展示内容硬编码在业务逻辑里。
