package dev.meirong.shop.archetype;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code bff-service-archetype}.
 * <p>
 * Verifies that projects generated from the bff-service-archetype:
 * </p>
 * <ul>
 *   <li>Have the expected directory structure</li>
 *   <li>Compile successfully</li>
 *   <li>Pass all tests</li>
 * </ul>
 */
class BffServiceArchetypeTest extends AbstractArchetypeTest {

    private static final String ARCHETYPE = "bff-service-archetype";
    private static final String ARTIFACT = "test-bff-service";

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

        // Verify standard Java structure
        assertStandardJavaStructure(projectDir);

        // Verify K8s structure
        assertK8sStructure(projectDir);

        // Verify bff-service specific files
        assertFileExists(projectDir, "pom.xml");
        assertFileExists(projectDir, "README.md");
        assertFileExists(projectDir, "src/main/resources/application.yml");

        // Verify Java source files exist
        assertThat(Files.list(projectDir.resolve("src/main/java")).count())
            .isGreaterThan(0);

        // Verify test files exist
        assertThat(Files.list(projectDir.resolve("src/test/java")).count())
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

        assertThat(projectDir.resolve("target/surefire-reports"))
            .exists()
            .isDirectory();
    }

    @Test
    void shouldGenerateWithCorrectDependencies() throws Exception {
        Path projectDir = generateProject();

        String pomContent = Files.readString(projectDir.resolve("pom.xml"));

        // Verify key dependencies are present
        assertThat(pomContent).contains("spring-boot-starter-web");
        assertThat(pomContent).contains("spring-boot-starter-actuator");
        assertThat(pomContent).contains("resilience4j-spring-boot3");
        assertThat(pomContent).contains("micrometer-registry-prometheus");
        assertThat(pomContent).contains("micrometer-tracing-bridge-otel");
        assertThat(pomContent).contains("springdoc-openapi");
        assertThat(pomContent).contains("shop-common-core");
        assertThat(pomContent).doesNotContain("<artifactId>shop-contracts</artifactId>");
    }
}
