package dev.meirong.shop.archetype;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code portal-service-archetype}.
 * <p>
 * Note: This archetype generates Kotlin + Thymeleaf projects.
 * </p>
 */
class PortalServiceArchetypeTest extends AbstractArchetypeTest {

    private static final String ARCHETYPE = "portal-service-archetype";
    private static final String ARTIFACT = "test-portal-service";

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

        // Portal uses Kotlin, not Java
        assertStandardKotlinStructure(projectDir);
        assertK8sStructure(projectDir);

        assertFileExists(projectDir, "pom.xml");
        assertFileExists(projectDir, "README.md");
        assertFileExists(projectDir, "src/main/resources/application.yml");

        // Verify Kotlin source directories
        assertDirectoryExists(projectDir, "src/main/kotlin");
        assertDirectoryExists(projectDir, "src/test/kotlin");

        // Verify Thymeleaf templates directory
        assertDirectoryExists(projectDir, "src/main/resources/templates");

        // Verify Kotlin source files exist
        assertThat(Files.list(projectDir.resolve("src/main/kotlin")).count())
            .isGreaterThan(0);
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

        assertThat(pomContent).contains("kotlin-stdlib");
        assertThat(pomContent).contains("kotlin-reflect");
        assertThat(pomContent).contains("jackson-module-kotlin");
        assertThat(pomContent).contains("spring-boot-starter-thymeleaf");
        assertThat(pomContent).contains("spring-boot-starter-web");
        assertThat(pomContent).contains("spring-boot-starter-actuator");
        assertThat(pomContent).contains("micrometer-registry-prometheus");
        assertThat(pomContent).contains("micrometer-tracing-bridge-otel");
        assertThat(pomContent).doesNotContain("<artifactId>shop-common</artifactId>");
        assertThat(pomContent).doesNotContain("<artifactId>shop-contracts</artifactId>");
    }
}
