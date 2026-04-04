# ${artifactId}

## 职责

用于承载统一入口路由、JWT 校验、跨服务网关过滤与协议适配。

## 依赖

- Spring Cloud Gateway
- Spring Security Resource Server
- Prometheus / OTLP / Actuator
- shop-contracts

## 接口

- 示例探活路由：`GET /gateway/v1/route-ping`
- OpenAPI：`http://localhost:8080/swagger-ui.html`

## 本地运行

```bash
./mvnw -pl ${artifactId} -am test
./mvnw -pl ${artifactId} -am spring-boot:run
```

## 部署

```bash
docker build --build-arg MODULE=${artifactId} -f docker/Dockerfile.module -t shop/${artifactId}:dev .
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/hpa.yaml
```
