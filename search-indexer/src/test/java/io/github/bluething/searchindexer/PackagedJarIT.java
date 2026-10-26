package io.github.bluething.searchindexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the shaded jar exactly as a user would: {@code java -jar search-indexer.jar ...}.
 * Catches what unit tests cannot: a broken manifest, missing shaded classes, real exit codes.
 */
class PackagedJarIT {

    @TempDir
    Path dir;

    @Test
    void indexesFilesFromTheCommandLine() throws Exception {
        Path txt = Files.writeString(dir.resolve("file1.txt"), "Hello World from the Indexing process.");
        Path html = Files.writeString(dir.resolve("file2.html"), "<p>Search résumé</p>");

        Result result = runJar(txt.toString(), html.toString(), dir.resolve("missing.txt").toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stdout()).contains(
                "Words longer than 5 characters: [Indexing, process]",
                "Words longer than 5 characters: [Search, résumé]");
        assertThat(result.stderr()).contains("missing.txt: file not found");
    }

    @Test
    void reportsTheProjectVersion() throws Exception {
        Result result = runJar("--version");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().strip()).matches("search-indexer \\d+\\.\\d+\\.\\d+.*");
    }

    private Result runJar(String... args) throws IOException, InterruptedException {
        String jar = System.getProperty("app.jar");
        assertThat(jar).as("app.jar system property (set by failsafe)").isNotBlank();

        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar));
        command.addAll(List.of(args));
        Path stdout = dir.resolve("stdout");
        Path stderr = dir.resolve("stderr");

        Process process = new ProcessBuilder(command)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("process finished").isTrue();
        } finally {
            // Never leave a stray JVM behind, whether we timed out, failed or were interrupted.
            if (process.isAlive()) {
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
        }

        return new Result(process.exitValue(),
                Files.readString(stdout, StandardCharsets.UTF_8),
                Files.readString(stderr, StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String stdout, String stderr) {
    }
}
