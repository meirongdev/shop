# ${artifactId}

## 职责

用于承载认证、授权、Token 生命周期与账号接入能力。

## 依赖

- Spring Security
- Validation / Actuator / Prometheus / OTLP
- shop-common / shop-contracts

## 接口

- 示例接口：`GET /auth/v1/ping`
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
