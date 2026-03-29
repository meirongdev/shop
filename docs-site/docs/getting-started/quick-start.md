---
title: 快速开始
---

# 快速开始

本页提供最快可运行路径（Kind），适合首次体验。

## 前置条件

- Docker Desktop（或任意兼容 Docker 的运行时）
- Kind
- kubectl

## 5 分钟跑起来

```bash
make e2e

# 另开一个终端，建立稳定访问入口
make local-access
```

默认入口（已验证）：

- Buyer Portal: `http://127.0.0.1:18080/buyer/login`
- Mailpit: `http://127.0.0.1:18025`
- Prometheus: `http://127.0.0.1:19090`
- Seller Web：不再通过 `/seller/login` 暴露 SSR 页面；仓库使用 `make ui-e2e` 对 KMP `seller-app` 做页面级校验，手工预览方式见 [`本地部署`](/getting-started/local-deployment)

## 快速健康检查

```bash
kubectl -n shop get pods
curl -fsS http://127.0.0.1:18080/actuator/health | jq -r '.status'
curl -fsS "http://127.0.0.1:18080/public/buyer/v1/product/search?q=hair" | head
```

如果没有 `jq`，可直接观察返回 JSON 中是否包含 `"status":"UP"`。

## 常见问题

- 服务未就绪：`kubectl -n shop get pods` 与 `kubectl -n shop logs <pod>`
- 没有跑 `make local-access`：先在另一个终端执行 `make local-access`
- 数据重置：`./kind/teardown.sh`

## 进阶

需要了解分步部署、热更新验证或更多冒烟步骤，继续阅读 [`本地部署`](/getting-started/local-deployment)。
