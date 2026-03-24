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
./kind/setup.sh
```

默认入口：

- Buyer Portal: `http://localhost:8080/buyer/login`
- Seller Portal: `http://localhost:8080/seller/login`
- Mailpit: `http://localhost:8025`
- Prometheus: `http://localhost:9090`

## 快速健康检查

```bash
kubectl -n shop get pods
curl -fsS http://localhost:8080/actuator/health | jq -r '.status'
curl -fsS "http://localhost:8080/public/buyer/v1/product/search?q=hair" | head
```

如果没有 `jq`，可直接观察返回 JSON 中是否包含 `"status":"UP"`。

## 常见问题

- 服务未就绪：`kubectl -n shop get pods` 与 `kubectl -n shop logs <pod>`
- 本地 8080 未暴露：`kubectl -n shop port-forward svc/api-gateway 18080:8080`
- 数据重置：`./kind/teardown.sh`

## 进阶

需要了解分步部署、热更新验证或更多冒烟步骤，继续阅读 [`本地部署`](/getting-started/local-deployment)。
