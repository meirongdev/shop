package dev.meirong.shop.archetype;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code auth-service-archetype}.
 */
class AuthServiceArchetypeTest extends AbstractArchetypeTest {

    private static final String ARCHETYPE = "auth-service-archetype";
    private static final String ARTIFACT = "test-auth-service";

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
        
        // Verify Auth specific directories
        assertDirectoryExists(projectDir, "src/main/java/dev/meirong/shop/testgen/controller");
        assertDirectoryExists(projectDir, "src/main/java/dev/meirong/shop/testgen/service");
        assertDirectoryExists(projectDir, "src/main/java/dev/meirong/shop/testgen/config");
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
        
        assertThat(pomContent).contains("spring-boot-starter-security");
        assertThat(pomContent).contains("spring-boot-starter-web");
        assertThat(pomContent).contains("spring-boot-starter-actuator");
        assertThat(pomContent).contains("micrometer-registry-prometheus");
        assertThat(pomContent).contains("micrometer-tracing-bridge-otel");
        assertThat(pomContent).contains("springdoc-openapi");
        assertThat(pomContent).contains("shop-common");
        assertThat(pomContent).contains("shop-contracts");
    }
}
