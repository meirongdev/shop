# ${artifactId}

## 职责

用于承载面向买家/卖家的 Web 门户、Thymeleaf 视图、表单交互与网关 API 访问。

## 依赖

- Spring MVC + Thymeleaf + Kotlin
- shop-common / shop-contracts
- Prometheus / OTLP / Actuator

## 接口

- 示例页面：`GET /`
- 门户默认通过视图渲染页面，可按需补充 OpenAPI 文档

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
