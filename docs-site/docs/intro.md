---
slug: /
sidebar_position: 1
title: 项目概览
---

# Shop Platform

基于 **Java 25 + Spring Boot 3.5 + Spring Cloud 2025.0** 的云原生微服务电商技术验证平台。

## 这是什么

Shop Platform 聚焦验证 **Gateway → Thin BFF → Domain Service** 三层模型在真实交易链路中的工程可行性，覆盖浏览、搜索、加购、结账、支付、通知、积分、活动与开放能力。

Redis / Redisson 在本项目中承担共享协调平面角色：既服务于限流、OTP、guest cart、活动防作弊和 Bloom Filter 幂等，也用于库存写入、优惠券发放和批处理调度的多实例协调。

## 服务全景

| 服务 | 端口 | 职责 |
|---|---:|---|
| api-gateway | 8080 | 统一入口、JWT 校验、Trusted Headers、限流与灰度 |
| buyer-bff | 8081 | 买家聚合：商品、购物车、结账、订单、积分 Hub |
| seller-bff | 8082 | 卖家聚合：商品、订单履约、促销、店铺管理 |
| profile-service | 8083 | 买家/卖家档案与店铺资料 |
| marketplace-service | 8084 | 商品目录、SKU、评价、库存 |
| order-service | 8085 | 订单状态机、结账、游客直购、订单追踪 |
| wallet-service | 8086 | 余额账户、充值/提现、支付意图 |
| promotion-service | 8087 | 促销与优惠券引擎（legacy + template/instance 双轨） |
| loyalty-service | 8088 | 积分账户、签到、兑换、新手任务 |
| activity-service | 8089 | 互动活动（砸金蛋、红包、集卡、农场） |
| auth-server | 8090 | 登录、JWT 签发、Google 社交登录、guest token |
| search-service | 8091 | Meilisearch 搜索、联想词、热词、开关控制 |
| notification-service | 8092 | Kafka 事件通知、邮件模板、幂等重试 |
| webhook-service | 8093 | 卖家 Webhook 订阅与投递 |
| subscription-service | 8094 | 订阅计划与订阅生命周期 |
| buyer-portal | 8100 | 买家 SSR 门户（Kotlin + Thymeleaf） |
| seller-portal | 8101 | 卖家 SSR 门户（Kotlin + Thymeleaf） |

## 推荐阅读路径

- 新同学快速上手：[`快速开始`](/getting-started/quick-start)
- 本地完整环境：[`本地部署`](/getting-started/local-deployment)
- 核心设计理念：[`架构设计`](/architecture)
- 服务职责分组：[`服务模块`](/services/core)
- 进度与优先级：[`Roadmap`](/roadmap)
