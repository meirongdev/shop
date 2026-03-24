---
title: 核心交易服务
---

# 核心交易服务

本分组包含直接决定“能否浏览、下单、支付、查单”的服务。

## 服务清单

| 服务 | 端口 | 职责 |
|---|---:|---|
| Auth Server | 8090 | 登录、JWT、社交登录、guest token |
| Profile Service | 8083 | 买家/卖家档案、店铺资料 |
| Marketplace Service | 8084 | 商品、SKU、评价、库存 |
| Order Service | 8085 | 购物车（已登录）、结账、订单状态机、游客直购 |
| Wallet Service | 8086 | 余额账户、充值/提现、支付意图 |

## Auth Server

关键 API：

- `POST /auth/v1/token/login`
- `POST /auth/v1/token/guest`
- `POST /auth/v1/token/oauth2/google`
- `GET /auth/v1/user/me`

说明：当前 Google 已接入，Apple/SMS OTP 仍属于 roadmap 未完成项。

## Profile Service

关键 API：

- `POST /profile/v1/profile/get`
- `POST /profile/v1/profile/update`
- `POST /profile/v1/seller/get`
- `POST /profile/v1/seller/shop/update`

说明：当前以“已有档案更新”为主，建档/注册流程在 auth/profile 联动中继续演进。

## Marketplace Service

关键 API：

- `POST /marketplace/v1/product/list`
- `POST /marketplace/v1/product/get`
- `POST /marketplace/v1/product/search`
- `POST /marketplace/v1/product/create`

说明：承担商品主数据与库存事实源；`search-service` 作为检索投影能力与其协同。同一商品的库存扣减/恢复现在通过 Redisson 分布式锁串行化，避免多实例下单与补偿并发时出现超卖或回写丢失。

## Order Service

关键 API：

- `POST /order/v1/checkout/create`
- `POST /order/v1/checkout/guest`
- `GET /order/v1/order/track?token=`
- `POST /order/v1/order/ship`
- `POST /internal/orders/payment-confirm`

说明：已登录购物车在 order-service；游客购物车在 buyer-bff Redis，登录后合并回 order-service。超时取消与自动确认收货两个定时任务现在通过 Redisson 分布式锁协调，避免多实例重复批处理。

## Wallet Service

关键 API：

- `POST /wallet/v1/account/get`
- `POST /wallet/v1/deposit/create`
- `POST /wallet/v1/withdraw/create`

说明：充值/提现与 outbox 事件在服务内同事务落地，支持后续金融链路解耦。
