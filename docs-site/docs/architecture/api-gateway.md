---
sidebar_position: 2
title: API Gateway
---

# API Gateway（网关服务）

统一 northbound 入口，基于 **Spring Cloud Gateway Server Web MVC + Virtual Threads** 提供 YAML 路由、JWT 校验、Trusted Headers 注入、CORS、限流与 Canary。

## 路由规则

| 路径模式 | 目标服务 | 认证要求 |
|---------|---------|---------|
| `/auth/**` | auth-server | 无 |
| `/buyer/**` | buyer-portal (SSR) | 无 |
| `/buyer-app/**` | buyer-app (KMP WASM nginx) | 无 |
| `/seller/**` | seller-portal (KMP WASM nginx) | 无 |
| `/api/buyer/**` | buyer-bff | JWT |
| `/api/seller/**` | seller-bff | JWT |
| `/api/loyalty/**` | loyalty-service | JWT |
| `/api/activity/**` | activity-service | JWT |
| `/api/webhook/**` | webhook-service | JWT |
| `/api/subscription/**` | subscription-service | JWT |
| `/public/buyer/**` | buyer-bff | 无（当前主要保留作匿名北向扩展入口） |

> `search-service` 不经过 Gateway，BFF 通过内部地址直接调用。这是有意设计：搜索已在 BFF 层完成鉴权与编排，无需多一跳 northbound 转发。

## Guest Buyer 说明

- `auth-server` 支持 `/auth/v1/token/guest`
- `buyer-portal` 使用 guest JWT（`ROLE_BUYER_GUEST`）访问 `/api/buyer/**`
- 因此“游客模式”并不是匿名访问，而是**受限能力的 guest principal**

## Trusted Headers

Gateway 对 `/api/**` 路径的已认证请求注入以下头：

| Header | 来源 |
|--------|------|
| `X-Request-Id` | 请求头或自动生成 UUID |
| `X-Buyer-Id` | JWT claim: principalId |
| `X-Username` | JWT claim: username |
| `X-Roles` | JWT claim: roles |
| `X-Portal` | JWT claim: portal |
| `X-Internal-Token` | 配置项 |

## 安全策略

- `/actuator/**`, `/auth/**`, `/buyer/**`, `/buyer-app/**`, `/seller/**`, `/public/**` — 公开访问
- `/api/**` — 需要有效 JWT
- Gateway 会移除外部请求伪造的 Trusted Headers，再按 JWT 重新注入
- 更细粒度的角色 / guest 约束由 BFF 和领域服务继续执行

## CORS

当前通过 Spring Security `CorsConfigurationSource` 统一管理：

- 生效路径：`/api/**`、`/public/**`
- 默认 origin patterns：
  - `http://localhost:*`
  - `http://127.0.0.1:*`
  - `http://[::1]:*`
- 默认允许方法：`GET,POST,PUT,PATCH,DELETE,OPTIONS`
- 默认允许头：`Authorization,Content-Type,X-Request-Id,X-Device-Fingerprint`
- 默认暴露头：`X-Request-Id`, `X-Trace-Id`

## 流量治理

- **Virtual Threads**：Gateway 默认启用 `spring.threads.virtual.enabled=true`
- **Rate Limiting**：`RateLimitingFilter` 使用 Redis Lua 脚本按 `X-Buyer-Id` / IP 做基础限流；Redis 故障时 fail-open
- **Canary**：buyer / seller API 支持 Redis Set 白名单灰度，命中后优先走 `*_V2_URI`
- **YAML 路由治理**：所有 northbound 路由在 `application.yml` 中维护

## 配置

| 配置项 | 环境变量 | 说明 |
|--------|---------|------|
| JWT Secret | `SHOP_AUTH_JWT_SECRET` | 与 Auth Server 共享 |
| Internal Token | `SHOP_INTERNAL_TOKEN` | 服务间认证 |
| Redis Host | `GATEWAY_REDIS_HOST` | 默认 `redis` |
| Redis Port | `GATEWAY_REDIS_PORT` | 默认 `6379` |
| Rate Limit RPM | `GATEWAY_RATE_LIMIT_RPM` | 默认 `100` |
| Rate Limit Burst | `GATEWAY_RATE_LIMIT_BURST` | 默认 `20` |
| Allowed Origin Patterns | `GATEWAY_ALLOWED_ORIGIN_PATTERNS` | CORS 白名单 |
| Allowed Methods | `GATEWAY_ALLOWED_METHODS` | CORS 方法 |
| Allowed Headers | `GATEWAY_ALLOWED_HEADERS` | CORS 请求头 |
| Exposed Headers | `GATEWAY_EXPOSED_HEADERS` | CORS 响应暴露头 |
| Allow Credentials | `GATEWAY_ALLOW_CREDENTIALS` | 默认 `true` |
| CORS Max Age | `GATEWAY_CORS_MAX_AGE_SECONDS` | 默认 `3600` |

## 验证

- `./mvnw -q -pl api-gateway -am test`
- 已有测试覆盖 CORS preflight 与 `GatewayProperties` 绑定
- Kind / Kubernetes 下建议继续显式关闭 `enableServiceLinks`，避免 `REDIS_PORT=tcp://...` 之类环境变量污染配置绑定
