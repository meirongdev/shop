# ${artifactId}

## 职责

用于聚合买家/卖家侧下游服务能力，负责接口裁剪、并发聚合与容错。

## 依赖

- Spring Web + Validation
- Resilience4j
- shop-common / shop-contracts
- Prometheus / OTLP / Actuator

## 接口

- 示例接口：`GET /bff/v1/ping`
- OpenAPI：`http://localhost:8080/swagger-ui.html`

## 本地运行

```bash
./mvnw -pl ${artifactId} -am test
./mvnw -pl ${artifactId} -am spring-boot:run
```

## 部署

```bash
make build-images
# or to build and deploy a single module:
make redeploy MODULE=${artifactId}
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/hpa.yaml
```
