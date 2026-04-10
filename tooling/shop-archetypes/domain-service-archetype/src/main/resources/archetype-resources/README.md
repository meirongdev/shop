# ${artifactId}

## 职责

用于承载核心领域建模、JPA/Flyway 迁移、外部 API 与内部事件落库逻辑。

## 依赖

- Spring Web + Data JPA + Flyway
- MySQL
- shop-common / shop-contracts
- Testcontainers 基座

## 接口

- 示例接口：`GET /domain/v1/ping`
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
