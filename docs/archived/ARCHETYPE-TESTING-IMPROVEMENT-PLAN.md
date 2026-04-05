# Archetype 自动化测试改进方案

> 版本：1.0 | 创建日期：2026-04-01
> 状态：实施中

---

## 一、现状分析

### 1.1 当前已有的测试保障

| 测试类型 | 状态 | 说明 |
|---------|------|------|
| Archetype 本身编译 | ✅ 已有 | Maven verify 覆盖 |
| 生成项目的测试模板 | ✅ 已有 | 每个 archetype 都包含测试文件 |
| Testcontainers 基座 | ⚠️ 部分 | domain/event-worker 有提供 |

### 1.2 当前缺失的能力

| 测试类型 | 状态 | 风险 |
|---------|------|------|
| **自动生成验证测试** | ❌ 缺失 | 模板变更后无法自动发现回归问题 |
| **CI 生成流水线** | ❌ 缺失 | 无法在 PR 合并前验证 archetype 可用性 |
| **Makefile 测试命令** | ❌ 缺失 | 本地开发无法快速验证 archetype |

### 1.3 问题影响

当前 archetype 的验证依赖**手动执行生成命令**，存在以下问题：

1. **反馈延迟**：只有手动生成后执行 `mvn test` 才能发现问题
2. **容易遗漏**：开发完成后可能忘记验证 archetype
3. **CI 不覆盖**：PR 合并后可能破坏 archetype 生成能力
4. **文档与实现脱节**：文档中的生成命令可能已过时但无人发现

---

## 二、改进目标

### 2.1 短期目标（P0）

- [ ] 创建 `archetype-tests` 模块，包含所有 archetype 的生成验证测试
- [ ] 添加 `make archetype-test` 命令
- [ ] 创建 `scripts/test-archetypes.sh` 脚本
- [ ] 在 CI 中添加 archetype 测试 job

### 2.2 中期目标（P1）

- [ ] 为每个 archetype 添加多版本 JDK 测试（Java 21/25）
- [ ] 添加 archetype 变更检测（仅测试受影响的 archetype）
- [ ] 生成测试覆盖率报告

### 2.3 长期目标（P2）

- [ ] archetype 契约测试（验证生成项目的 API 一致性）
- [ ] archetype 性能测试（生成速度、编译速度）
- [ ] archetype 文档自动生成（从测试生成示例文档）

---

## 三、技术方案

### 3.1 整体架构

```
shop-archetypes/
├── gateway-service-archetype/
├── auth-service-archetype/
├── bff-service-archetype/
├── domain-service-archetype/
├── event-worker-archetype/
└── portal-service-archetype/

archetype-tests/                    # 新增模块
├── pom.xml
└── src/
    └── test/
        └── java/
            └── dev/meirong/shop/archetype/
                ├── AbstractArchetypeTest.java
                ├── GatewayServiceArchetypeTest.java
                ├── AuthServiceArchetypeTest.java
                ├── BffServiceArchetypeTest.java
                ├── DomainServiceArchetypeTest.java
                ├── EventWorkerArchetypeTest.java
                └── PortalServiceArchetypeTest.java

scripts/
├── test-archetypes.sh              # 新增脚本
└── ...
```

### 3.2 测试策略

每个 archetype 测试验证以下内容：

```java
@Test
void archetypeShouldGenerateCompilableProject() {
    // 1. 使用 archetype 生成项目到临时目录
    // 2. 执行 mvn compile，验证编译通过
    // 3. 执行 mvn test，验证测试通过
    // 4. 验证生成的目录结构符合预期
    // 5. 清理临时文件
}
```

### 3.3 测试基类设计

创建 `AbstractArchetypeTest` 提供通用能力：

```java
public abstract class AbstractArchetypeTest {
    
    protected static final String GROUP_ID = "dev.meirong.shop";
    protected static final String VERSION = "0.1.0-SNAPSHOT";
    protected static final String PACKAGE = "dev.meirong.shop.test";
    
    protected Path generateProject(String archetypeArtifactId, String artifactId) {
        // 使用 Maven Archetype Plugin 生成项目
    }
    
    protected void compileProject(Path projectDir) {
        // 执行 mvn compile
    }
    
    protected void testProject(Path projectDir) {
        // 执行 mvn test
    }
    
    protected void cleanup(Path projectDir) {
        // 清理临时文件
    }
}
```

### 3.4 测试依赖

```xml
<dependencies>
    <!-- Maven Invoker Plugin（用于调用 Maven 命令） -->
    <dependency>
        <groupId>org.apache.maven.shared</groupId>
        <artifactId>maven-invoker</artifactId>
        <version>3.3.0</version>
        <scope>test</scope>
    </dependency>
    
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- AssertJ（流式断言） -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Awaitility（异步等待） -->
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 四、实施步骤

### 4.1 步骤 1：创建 archetype-tests 模块

**文件**：`archetype-tests/pom.xml`

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>dev.meirong.shop</groupId>
        <artifactId>shop-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    
    <artifactId>archetype-tests</artifactId>
    <name>archetype-tests</name>
    <packaging>jar</packaging>
    
    <properties>
        <maven-invoker.version>3.3.0</maven-invoker.version>
        <awaitility.version>4.2.2</awaitility.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.apache.maven.shared</groupId>
            <artifactId>maven-invoker</artifactId>
            <version>${maven-invoker.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <version>${awaitility.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### 4.2 步骤 2：创建测试基类

**文件**：`archetype-tests/src/test/java/dev/meirong/shop/archetype/AbstractArchetypeTest.java`

核心方法：
- `generateProject()` - 生成项目
- `compileProject()` - 编译验证
- `testProject()` - 测试验证
- `assertFileExists()` - 文件存在性断言
- `cleanup()` - 清理临时文件

### 4.3 步骤 3：创建各 Archetype 测试类

每个 archetype 一个测试类，例如：

```java
class DomainServiceArchetypeTest extends AbstractArchetypeTest {
    
    private static final String ARCHETYPE = "domain-service-archetype";
    private static final String ARTIFACT = "test-domain-service";
    
    @Test
    void shouldGenerateCompilableProject() throws Exception {
        Path projectDir = generateProject(ARCHETYPE, ARTIFACT);
        compileProject(projectDir);
        assertFileExists(projectDir, "pom.xml");
        assertFileExists(projectDir, "src/main/java");
        assertFileExists(projectDir, "src/test/java");
        assertFileExists(projectDir, "src/main/resources/db/migration");
    }
    
    @Test
    void shouldGeneratePassingTests() throws Exception {
        Path projectDir = generateProject(ARCHETYPE, ARTIFACT);
        testProject(projectDir);
    }
}
```

### 4.4 步骤 4：创建测试脚本

**文件**：`scripts/test-archetypes.sh`

```bash
#!/bin/bash
set -euo pipefail

echo "=== Archetype Generation Tests ==="
echo ""

# 安装 archetype 到本地仓库
echo "Step 1: Installing archetypes..."
./mvnw -pl shop-common,shop-contracts,shop-archetypes -am install -q

# 运行 archetype 测试模块
echo "Step 2: Running archetype generation tests..."
./mvnw -pl archetype-tests test

echo ""
echo "=== All archetype tests passed ==="
```

### 4.5 步骤 5：更新 Makefile

**添加目标**：

```makefile
archetype-test: ## Run archetype generation tests
	@echo "Running archetype generation tests..."
	bash ./scripts/test-archetypes.sh

.PHONY: archetype-test
```

### 4.6 步骤 6：更新 CI 工作流

**文件**：`.github/workflows/ci.yml`

添加新 job：

```yaml
archetype-test:
  needs: changes
  if: github.event_name != 'pull_request' || needs.changes.outputs.maven == 'true'
  runs-on: ubuntu-latest
  timeout-minutes: 20
  steps:
    - name: Checkout
      uses: actions/checkout@v4
      
    - name: Set up JDK 25
      uses: actions/setup-java@v4
      with:
        java-version: '25'
        distribution: 'temurin'
        cache: 'maven'
    
    - name: Install archetypes
      run: ./mvnw -pl shop-common,shop-contracts,shop-archetypes -am install -B -ntp
    
    - name: Run archetype tests
      run: ./mvnw -pl archetype-tests test -B -ntp
    
    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: archetype-test-results
        path: '**/archetype-tests/target/surefire-reports/*.xml'
```

---

## 五、验收标准

### 5.1 功能验收

- [ ] `make archetype-test` 命令可执行
- [ ] 所有 6 个 archetype 都能通过生成测试
- [ ] CI 中 archetype-test job 运行成功
- [ ] 测试失败时有清晰的错误信息

### 5.2 性能验收

- [ ] 单个 archetype 测试 < 3 分钟
- [ ] 全部 archetype 测试 < 15 分钟
- [ ] 临时文件正确清理，无残留

### 5.3 文档验收

- [ ] 本文档已更新实施状态
- [ ] `shop-archetypes/README.md` 已添加测试说明
- [ ] `ARCHETYPE-TUTORIAL.md` 已添加测试章节

---

## 六、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 测试执行时间长 | CI 反馈慢 | 并行执行 archetype 测试 |
| 临时文件清理失败 | 磁盘空间耗尽 | 使用 JUnit @TempDir，自动清理 |
| Maven 依赖下载慢 | 测试超时 | CI 使用 Maven 缓存 |
| Testcontainers 资源竞争 | 测试失败 | 限制并发数，使用固定端口 |

---

## 七、后续演进

### 7.1 契约测试

验证 archetype 生成的项目 API 一致性：

```java
@Test
void shouldGenerateExpectedApiEndpoints() {
    Path projectDir = generateProject(ARCHETYPE, ARTIFACT);
    // 启动生成的项目
    // 调用 /actuator/health
    // 调用 /swagger-ui.html
    // 验证响应符合预期
}
```

### 7.2 多 JDK 版本测试

```yaml
strategy:
  matrix:
    java: [21, 25]
```

### 7.3 文档自动生成

从测试代码生成 archetype 使用示例文档，确保文档与实现一致。

---

## 八、参考资源

| 资源 | 链接 |
|------|------|
| Maven Archetype Plugin | https://maven.apache.org/archetype/maven-archetype-plugin/ |
| Maven Invoker | https://maven.apache.org/shared/maven-invoker/ |
| JUnit 5 | https://junit.org/junit5/ |
| AssertJ | https://assertj.github.io/doc/ |
| Awaitility | https://awaitility.org/ |

---

**最后更新**：2026-04-01  
**维护者**：Shop Platform Team  
**状态**：实施中
