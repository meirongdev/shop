package dev.meirong.shop.archetype;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code domain-service-archetype}.
 * <p>
 * Verifies that projects generated from the domain-service-archetype:
 * </p>
 * <ul>
 *   <li>Have the expected directory structure</li>
 *   <li>Compile successfully</li>
 *   <li>Pass all tests</li>
 * </ul>
 */
class DomainServiceArchetypeTest extends AbstractArchetypeTest {

    private static final String ARCHETYPE = "domain-service-archetype";
    private static final String ARTIFACT = "test-domain-service";

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
        // Generate project from archetype
        Path projectDir = generateProject();

        // Verify standard Java structure
        assertStandardJavaStructure(projectDir);

        // Verify K8s structure
        assertK8sStructure(projectDir);

        // Verify domain-service specific files
        assertFileExists(projectDir, "pom.xml");
        assertFileExists(projectDir, "README.md");
        assertFileExists(projectDir, "src/main/resources/application.yml");

        // Verify Flyway migration directory
        assertDirectoryExists(projectDir, "src/main/resources/db/migration");

        // Verify Java source files exist
        assertThat(Files.list(projectDir.resolve("src/main/java")).count())
            .isGreaterThan(0);

        // Verify test files exist
        assertThat(Files.list(projectDir.resolve("src/test/java")).count())
            .isGreaterThan(0);
    }

    @Test
    void shouldGenerateCompilableProject() throws Exception {
        // Generate and compile
        Path projectDir = generateProject();
        compileProject(projectDir);

        // If compileProject() doesn't throw, compilation succeeded
        assertThat(projectDir.resolve("target/classes")).exists();
    }

    @Test
    void shouldGenerateProjectWithPassingTests() throws Exception {
        // Generate and run tests
        Path projectDir = generateProject();
        testProject(projectDir);

        // Verify test reports were generated
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
        assertThat(pomContent).contains("spring-boot-starter-data-jpa");
        assertThat(pomContent).contains("flyway-core");
        assertThat(pomContent).contains("flyway-mysql");
        assertThat(pomContent).contains("mysql-connector-j");
        assertThat(pomContent).contains("micrometer-registry-prometheus");
        assertThat(pomContent).contains("micrometer-tracing-bridge-otel");
        assertThat(pomContent).contains("springdoc-openapi");
        assertThat(pomContent).contains("shop-common-core");
        assertThat(pomContent).doesNotContain("<artifactId>shop-contracts</artifactId>");
    }
}
