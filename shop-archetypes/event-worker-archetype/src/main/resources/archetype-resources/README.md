# ${artifactId}

## 职责

用于承载 Kafka 事件消费、事件补偿、落库与后台处理任务。

## 依赖

- Spring Kafka + Data JPA + Flyway
- shop-common / shop-contracts
- Testcontainers（Kafka + MySQL）

## 接口

- 示例探活：`GET /worker/v1/ping`
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
