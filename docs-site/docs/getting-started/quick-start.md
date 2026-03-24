---
title: 快速开始
---

# 快速开始

本页提供最快可运行路径（Docker Compose），适合首次体验。

## 前置条件

- Docker Desktop（含 Docker Compose）
- Java 25
- Maven 3.9+

## 5 分钟跑起来

```bash
cp .env.example .env
./mvnw -q -DskipTests package
docker compose up -d
```

默认入口：

- Buyer Portal: `http://localhost:8080/buyer/login`
- Seller Portal: `http://localhost:8080/seller/login`
- Mailpit: `http://localhost:8025`
- Prometheus: `http://localhost:9090`

## 快速健康检查

```bash
curl -fsS http://localhost:8080/actuator/health | jq -r '.status'
curl -fsS "http://localhost:8080/public/buyer/v1/product/search?q=hair" | head
```

如果没有 `jq`，可直接观察返回 JSON 中是否包含 `"status":"UP"`。

## 常见问题

- 服务未就绪：`docker compose ps` 与 `docker compose logs <service>`
- Kafka 启动慢：先等待 `kafka` healthy 再重试 API
- 数据重置：`docker compose down -v`

## 进阶：Kind + mirrord

需要验证 K8s/热更新链路时，继续阅读 [`本地部署`](/getting-started/local-deployment)。
