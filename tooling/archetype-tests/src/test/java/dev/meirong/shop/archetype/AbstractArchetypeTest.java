package dev.meirong.shop.archetype;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for archetype integration tests.
 * <p>
 * Provides common functionality for generating projects from archetypes
 * and verifying they compile and pass tests.
 * </p>
 */
public abstract class AbstractArchetypeTest {

    protected static final String GROUP_ID = "dev.meirong.shop";
    protected static final String ARCHETYPE_GROUP_ID = "dev.meirong.shop";
    protected static final String ARCHETYPE_VERSION = "0.1.0-SNAPSHOT";
    protected static final String PACKAGE = "dev.meirong.shop.testgen";
    protected static final String SHOP_PLATFORM_VERSION = "0.1.0-SNAPSHOT";
    private static final Object INSTALL_LOCK = new Object();
    private static volatile boolean localArtifactsInstalled = false;

    @TempDir
    protected Path tempDir;

    /**
     * Returns the archetype artifact ID to test (e.g., "domain-service-archetype").
     */
    protected abstract String getArchetypeArtifactId();

    /**
     * Returns the artifact ID to use for the generated project.
     */
    protected abstract String getArtifactId();

    /**
     * Locates the {@code mvnw} wrapper in the nearest ancestor directory.
     */
    private File findMvnw() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            File mvnw = new File(dir, "mvnw");
            if (mvnw.exists()) {
                return mvnw;
            }
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("Cannot find mvnw in any ancestor of " + System.getProperty("user.dir"));
    }

    private void ensureLocalArtifactsInstalled() throws Exception {
        if (localArtifactsInstalled) {
            return;
        }
        synchronized (INSTALL_LOCK) {
            if (localArtifactsInstalled) {
                return;
            }
            File mvnw = findMvnw();
            File repoRoot = mvnw.getParentFile();
            runMavenCommand(
                    mvnw,
                    repoRoot,
                    List.of("-q", "-B", "-ntp", "-f", "shared/shop-common/pom.xml", "-DskipTests", "install"),
                    "Failed to install shop-common artifacts");
            runMavenCommand(
                    mvnw,
                    repoRoot,
                    List.of("-q", "-B", "-ntp", "-f", "shared/shop-contracts/pom.xml", "-DskipTests", "install"),
                    "Failed to install shop-contracts artifacts");
            runMavenCommand(
                    mvnw,
                    repoRoot,
                    List.of("-q", "-B", "-ntp", "-f", "tooling/shop-archetypes/pom.xml", "-DskipTests", "install"),
                    "Failed to install archetype artifacts");
            localArtifactsInstalled = true;
        }
    }

    private void runMavenCommand(File mvnw, File workingDirectory, List<String> arguments, String failureMessage)
            throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(mvnw.getAbsolutePath());
        cmd.addAll(arguments);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDirectory);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(failureMessage + ":\n" + new String(output));
        }
    }

    /**
     * Generates a project from the archetype and returns the project directory.
     */
    protected Path generateProject() throws Exception {
        return generateProject(getArtifactId());
    }

    /**
     * Generates a project from the archetype with a custom artifact ID.
     * Runs {@code mvnw archetype:generate} inside {@link #tempDir} so the
     * generated project lands at {@code tempDir/<artifactId>}.
     */
    protected Path generateProject(String artifactId) throws Exception {
        ensureLocalArtifactsInstalled();
        List<String> cmd = new ArrayList<>();
        cmd.add(findMvnw().getAbsolutePath());
        cmd.add("archetype:generate");
        cmd.add("-B");
        cmd.add("-DarchetypeGroupId=" + ARCHETYPE_GROUP_ID);
        cmd.add("-DarchetypeArtifactId=" + getArchetypeArtifactId());
        cmd.add("-DarchetypeVersion=" + ARCHETYPE_VERSION);
        cmd.add("-DgroupId=" + GROUP_ID);
        cmd.add("-DartifactId=" + artifactId);
        cmd.add("-Dpackage=" + PACKAGE);
        cmd.add("-DshopPlatformVersion=" + SHOP_PLATFORM_VERSION);
        cmd.add("-DinteractiveMode=false");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        // Drain output before waitFor() to prevent buffer-full deadlock
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(String.format(
                "archetype:generate failed for %s (exit code %d):%n%s",
                getArchetypeArtifactId(), exitCode, new String(output)));
        }

        Path projectDir = tempDir.resolve(artifactId);
        assertThat(projectDir).exists().isDirectory();
        return projectDir;
    }

    /**
     * Compiles the generated project.
     */
    protected void compileProject(Path projectDir) throws Exception {
        assertThat(projectDir).exists().isDirectory();

        File pomFile = projectDir.resolve("pom.xml").toFile();
        assertThat(pomFile).exists();

        Invoker invoker = new DefaultInvoker();
        invoker.setMavenExecutable(findMvnw());

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(pomFile);
        request.setGoals(List.of("compile"));
        request.setBatchMode(true);
        request.setQuiet(true);
        request.setJavaHome(new File(System.getProperty("java.home")));

        try {
            var result = invoker.execute(request);
            if (result.getExitCode() != 0) {
                throw new AssertionError("compile failed for " + projectDir.getFileName()
                    + " (exit code " + result.getExitCode() + ")");
            }
        } catch (MavenInvocationException e) {
            throw new AssertionError(
                "Failed to compile generated project " + projectDir.getFileName(), e);
        }
    }

    /**
     * Runs tests on the generated project.
     */
    protected void testProject(Path projectDir) throws Exception {
        assertThat(projectDir).exists().isDirectory();

        File pomFile = projectDir.resolve("pom.xml").toFile();
        assertThat(pomFile).exists();

        Invoker invoker = new DefaultInvoker();
        invoker.setMavenExecutable(findMvnw());

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(pomFile);
        request.setGoals(List.of("test"));
        request.setBatchMode(true);
        request.setJavaHome(new File(System.getProperty("java.home")));

        try {
            var result = invoker.execute(request);
            if (result.getExitCode() != 0) {
                throw new AssertionError("test failed for " + projectDir.getFileName()
                    + " (exit code " + result.getExitCode() + ")");
            }
        } catch (MavenInvocationException e) {
            throw new AssertionError(
                "Failed to run tests on generated project " + projectDir.getFileName(), e);
        }
    }

    /**
     * Asserts that a file exists in the generated project.
     */
    protected void assertFileExists(Path projectDir, String relativePath) {
        assertThat(projectDir.resolve(relativePath))
            .as("Expected file %s to exist in generated project", relativePath)
            .exists();
    }

    /**
     * Asserts that a directory exists in the generated project.
     */
    protected void assertDirectoryExists(Path projectDir, String relativePath) {
        assertThat(projectDir.resolve(relativePath))
            .as("Expected directory %s to exist in generated project", relativePath)
            .exists()
            .isDirectory();
    }

    /**
     * Verifies the standard Java source directory structure exists.
     */
    protected void assertStandardJavaStructure(Path projectDir) {
        assertDirectoryExists(projectDir, "src/main/java");
        assertDirectoryExists(projectDir, "src/test/java");
        assertDirectoryExists(projectDir, "src/main/resources");
    }

    /**
     * Verifies the standard Kotlin source directory structure exists.
     */
    protected void assertStandardKotlinStructure(Path projectDir) {
        assertDirectoryExists(projectDir, "src/main/kotlin");
        assertDirectoryExists(projectDir, "src/test/kotlin");
        assertDirectoryExists(projectDir, "src/main/resources");
    }

    /**
     * Verifies the standard K8s directory structure exists.
     */
    protected void assertK8sStructure(Path projectDir) {
        assertDirectoryExists(projectDir, "k8s");
        assertFileExists(projectDir, "k8s/deployment.yaml");
        assertFileExists(projectDir, "k8s/service.yaml");
        assertFileExists(projectDir, "k8s/hpa.yaml");
    }

    /**
     * Cleans up the generated project directory.
     */
    protected void cleanup(Path projectDir) throws IOException {
        if (Files.exists(projectDir)) {
            Files.walk(projectDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }
}
