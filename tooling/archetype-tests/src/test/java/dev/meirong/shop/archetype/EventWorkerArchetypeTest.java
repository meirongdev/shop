package dev.meirong.shop.archetype;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code event-worker-archetype}.
 */
class EventWorkerArchetypeTest extends AbstractArchetypeTest {

    private static final String ARCHETYPE = "event-worker-archetype";
    private static final String ARTIFACT = "test-event-worker";

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
        
        // Verify Flyway migration directory
        assertDirectoryExists(projectDir, "src/main/resources/db/migration");
        
        // Verify Kafka listener directory exists
        assertDirectoryExists(projectDir, "src/main/java/dev/meirong/shop/testgen/listener");
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
        
        assertThat(pomContent).contains("spring-kafka");
        assertThat(pomContent).contains("spring-boot-starter-data-jpa");
        assertThat(pomContent).contains("flyway-core");
        assertThat(pomContent).contains("flyway-mysql");
        assertThat(pomContent).contains("spring-kafka-test");
        assertThat(pomContent).contains("testcontainers");
        assertThat(pomContent).contains("kafka");
    }
}
