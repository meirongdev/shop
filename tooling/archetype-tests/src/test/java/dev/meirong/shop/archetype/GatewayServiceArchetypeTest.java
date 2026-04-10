package dev.meirong.shop.archetype;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code gateway-service-archetype}.
 */
class GatewayServiceArchetypeTest extends AbstractArchetypeTest {

    private static final String ARCHETYPE = "gateway-service-archetype";
    private static final String ARTIFACT = "test-gateway-service";

    @Override
    protected String getArchetypeArtifactId() {
        return ARCHETYPE;
    }

    @Override
    protected String getArtifactId() {
        return ARTIFACT;
    }

    @Test
    void shouldGenerateProjectWithExpectedStructure() throws Exception {
        Path projectDir = generateProject();

        assertStandardJavaStructure(projectDir);
        assertK8sStructure(projectDir);

        assertFileExists(projectDir, "pom.xml");
        assertFileExists(projectDir, "README.md");
        assertFileExists(projectDir, "src/main/resources/application.yml");

        // Verify Gateway specific directories
        assertDirectoryExists(projectDir, "src/main/java/dev/meirong/shop/testgen/config");
        assertDirectoryExists(projectDir, "src/main/java/dev/meirong/shop/testgen/filter");
    }

    @Test
    void shouldGenerateCompilableProject() throws Exception {
        Path projectDir = generateProject();
        compileProject(projectDir);
        assertThat(projectDir.resolve("target/classes")).exists();
    }

    @Test
    void shouldGenerateProjectWithPassingTests() throws Exception {
        Path projectDir = generateProject();
        testProject(projectDir);
        assertThat(projectDir.resolve("target/surefire-reports")).exists().isDirectory();
    }

    @Test
    void shouldGenerateWithCorrectDependencies() throws Exception {
        Path projectDir = generateProject();

        String pomContent = Files.readString(projectDir.resolve("pom.xml"));

        assertThat(pomContent).contains("spring-cloud-starter-gateway-server-webflux");
        assertThat(pomContent).contains("spring-boot-starter-oauth2-resource-server");
        assertThat(pomContent).contains("spring-boot-starter-actuator");
        assertThat(pomContent).contains("micrometer-registry-prometheus");
        assertThat(pomContent).contains("micrometer-tracing-bridge-otel");
        assertThat(pomContent).contains("springdoc-openapi-starter-webflux-ui");
        assertThat(pomContent).doesNotContain("<artifactId>shop-contracts</artifactId>");
    }
}
