package io.github.bluething.searchindexer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bluething.searchindexer.cli.IndexCommand;
import io.github.bluething.searchindexer.document.ContentExtractors;
import io.github.bluething.searchindexer.indexing.DocumentIndexer;
import io.github.bluething.searchindexer.rule.IndexingRules;
import io.github.bluething.searchindexer.text.WordTokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end through the real wiring and picocli, minus the JVM exit. */
class MainTest {

    @TempDir
    Path dir;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @Test
    void indexesFilesInArgumentOrder() throws IOException {
        Path txt = Files.writeString(dir.resolve("file1.txt"), "You are to create an Indexing process.");
        Path html = Files.writeString(dir.resolve("file2.html"), "<h1>Java Engineer</h1><p>production quality</p>");

        int exit = run(txt.toString(), html.toString());

        assertThat(exit).isZero();
        assertThat(out.toString()).isEqualToNormalizingNewlines("""
                %s
                  Words starting with an upper-case letter: 2
                  Words longer than 5 characters: [create, Indexing, process]
                %s
                  Words starting with an upper-case letter: 2
                  Words longer than 5 characters: [Engineer, production, quality]
                """.formatted(txt, html));
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void failedFileGivesExitCodeOneButOthersAreStillIndexed() throws IOException {
        Path good = Files.writeString(dir.resolve("good.txt"), "Hello");

        int exit = run(dir.resolve("missing.txt").toString(), good.toString());

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).contains("missing.txt: file not found");
        assertThat(out.toString()).contains(good.toString(), "upper-case letter: 1");
    }

    @Test
    void jsonLinesFormat() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "Hello Indexing");

        int exit = run("--format", "jsonl", file.toString(), dir.resolve("missing.txt").toString());

        assertThat(exit).isEqualTo(1);
        var mapper = new ObjectMapper();
        var lines = out.toString().lines().map(line -> {
            try {
                return mapper.readTree(line);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }).toList();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).at("/results/uppercase-word-count/count").asInt()).isEqualTo(2);
        assertThat(lines.get(1).at("/error/reason").asText()).isEqualTo("not_found");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void sequentialAndParallelRunsProduceTheSameOutput() throws IOException {
        String[] files = new String[30];
        for (int i = 0; i < files.length; i++) {
            files[i] = Files.writeString(dir.resolve("f" + i + ".txt"), "Alpha beta Gamma delta-epsilon " + i).toString();
        }

        run(prepend("--parallelism=1", files));
        String sequential = out.toString();
        out.getBuffer().setLength(0);
        run(prepend("-p", "8", files));

        assertThat(out.toString()).isEqualTo(sequential);
    }

    @Test
    void noArgumentsIsAUsageError() {
        int exit = run();

        assertThat(exit).isEqualTo(2);
        assertThat(err.toString()).contains("Missing required parameter", "Usage:");
    }

    @Test
    void invalidOptionsAreUsageErrors() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "x");

        assertThat(run("--format", "xml", file.toString())).isEqualTo(2);
        assertThat(run("--parallelism", "0", file.toString())).isEqualTo(2);
        assertThat(run("--parallelism", "1073741824", file.toString())).isEqualTo(2);
        assertThat(err.toString()).contains("--parallelism must be between 1 and 1024");
    }

    @Test
    void helpListsOptionsAndExitCodes() {
        int exit = run("--help");

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("--format", "--parallelism", "Exit codes:");
    }

    @Test
    void versionWorksOutsideAPackagedJar() {
        int exit = run("--version");

        assertThat(exit).isZero();
        assertThat(out.toString()).startsWith("search-indexer ");
    }

    @Test
    void unwritableOutputAbortsWithExitCodeThree() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "Hello");

        int exit = Main.execute(new String[] {file.toString()}, brokenWriter(), new PrintWriter(err, true));

        assertThat(exit).isEqualTo(3);
        assertThat(err.toString()).contains("aborted: could not write output");
    }

    @Test
    void unwritableStderrAlsoAborts() {
        int exit = Main.execute(new String[] {dir.resolve("missing.txt").toString()},
                new PrintWriter(out, true), brokenWriter());

        assertThat(exit).isEqualTo(3);
    }

    @Test
    void reusedCommandLineStartsEachRunClean() throws IOException {
        Path good = Files.writeString(dir.resolve("good.txt"), "Hello");
        var indexer = new DocumentIndexer(ContentExtractors.defaults(), new WordTokenizer(), IndexingRules.defaults());
        var commandLine = IndexCommand.commandLine(indexer, new PrintWriter(out, true), new PrintWriter(err, true));

        assertThat(commandLine.execute(dir.resolve("missing.txt").toString())).isEqualTo(1);
        assertThat(commandLine.execute(good.toString())).isZero();
    }

    private static PrintWriter brokenWriter() {
        return new PrintWriter(new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("broken pipe");
            }

            @Override
            public void flush() throws IOException {
                throw new IOException("broken pipe");
            }

            @Override
            public void close() {
            }
        });
    }

    private int run(String... args) {
        return Main.execute(args, new PrintWriter(out, true), new PrintWriter(err, true));
    }

    private static String[] prepend(String first, String... rest) {
        String[] all = new String[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    private static String[] prepend(String first, String second, String... rest) {
        return prepend(first, prepend(second, rest));
    }
}
