---
title: 核心交易服务
---

# 核心交易服务

本分组包含直接决定“能否建档、上架、下单、支付、查单”的领域服务。身份入口与聚合层请看 [`入口与聚合服务`](/services/edge-and-entry)。

## 服务清单

| 服务 | 主职责 | 主技术栈 | 可扩展点 |
|---|---|---|---|
| `profile-service` | 买家/卖家档案、地址簿、店铺资料 | Spring Boot、JPA、Flyway、MySQL | profile 字段、repository 查询、校验规则 |
| `marketplace-service` | 商品目录、SKU、评价、库存事实源 | Spring Boot、JPA、Flyway、MySQL、Redisson、Kafka | 商品模型、库存锁策略、outbox 事件、补偿任务 |
| `order-service` | 下单、订单状态机、登录态购物车、批处理 | Spring Boot、JPA、Flyway、MySQL、Kafka、Redisson | 状态流转、outbox、调度策略、幂等键 |
| `wallet-service` | 余额账户、充值/提现、支付意图 | Spring Boot、JPA、Flyway、MySQL、Kafka、Redisson、Stripe SDK | `StripeGateway`、支付提供方路由、mock/live 切换 |

## profile-service

关键 API：

- `POST /profile/v1/profile/get`
- `POST /profile/v1/profile/update`
- `POST /profile/v1/seller/get`
- `POST /profile/v1/seller/shop/update`

扩展思路：

- 追加档案字段、地址或店铺资料时，优先沿 JPA model + migration 方式演进。
- 任何“用户资料事实”都应继续留在 profile 域，不要复制到 BFF / Portal 做第二份真相。

## marketplace-service

关键 API：

- `POST /marketplace/v1/product/list`
- `POST /marketplace/v1/product/get`
- `POST /marketplace/v1/product/search`
- `POST /marketplace/v1/product/create`

扩展思路：

- 商品、SKU、评价、库存属于同一事实域，优先在这里演进数据模型。
- 库存修改已通过 Redisson 锁串行化，多实例并发时不要绕过这一层直接写库。
- 如果要把商品变化广播给别的服务，沿 `MarketplaceOutboxPublisher` 与 Kafka topic 继续扩展。

## order-service

关键 API：

- `POST /order/v1/checkout/create`
- `POST /order/v1/checkout/guest`
- `GET /order/v1/order/track?token=`
- `POST /order/v1/order/ship`
- `POST /internal/orders/payment-confirm`

扩展思路：

- 新状态、新补偿动作、新自动任务，应继续收敛在订单域的状态机与 scheduler 中。
- outbox 事件是订单对外传播事实的默认方式，不建议在事务里直接同步调多个下游。

## wallet-service

关键 API：

- `POST /wallet/v1/account/get`
- `POST /wallet/v1/deposit/create`
- `POST /wallet/v1/withdraw/create`

扩展思路：

- 支付提供方已经通过 `StripeGateway` 抽象出来，新增 PayPal / Apple Pay / Google Pay 时应沿这一接口扩展。
- 钱包流水与 outbox 事件在服务内同事务落地，方便后续把支付链路解耦给订阅、促销或对账服务。
