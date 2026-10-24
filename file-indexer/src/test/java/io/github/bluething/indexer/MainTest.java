package io.github.bluething.indexer;

import io.github.bluething.indexer.rule.IndexingRule;
import io.github.bluething.indexer.rule.RuleResult;
import io.github.bluething.indexer.rule.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void indexesTextAndHtmlFiles() throws IOException {
        Path txt = Files.writeString(dir.resolve("file1.txt"),
                "You are to create an Indexing process for a Search system.");
        Path html = Files.writeString(dir.resolve("file2.html"),
                "<html><body><h1>Java Engineer</h1><p>production quality</p></body></html>");

        int exit = run(txt.toString(), html.toString());

        assertThat(exit).isEqualTo(Main.OK);
        assertThat(stdout()).isEqualTo("""
                %s
                  Words starting with an upper-case letter: 3
                  Words longer than 5 characters: [create, Indexing, process, Search, system]
                %s
                  Words starting with an upper-case letter: 2
                  Words longer than 5 characters: [Engineer, production, quality]
                """.formatted(txt, html));
        assertThat(stderr()).isEmpty();
    }

    @Test
    void emptyFileProducesZeroResults() throws IOException {
        Path empty = Files.createFile(dir.resolve("empty.txt"));

        int exit = run(empty.toString());

        assertThat(exit).isEqualTo(Main.OK);
        assertThat(stdout()).contains(": 0", ": []");
    }

    @Test
    void missingFileIsReportedAndOtherFilesStillIndexed() throws IOException {
        Path good = Files.writeString(dir.resolve("good.txt"), "Hello");
        Path missing = dir.resolve("missing.txt");

        int exit = run(missing.toString(), good.toString());

        assertThat(exit).isEqualTo(Main.SOME_FILES_FAILED);
        assertThat(stderr()).contains("missing.txt", "file not found");
        assertThat(stdout()).contains(good.toString(), "Words starting with an upper-case letter: 1");
    }

    @Test
    void directoryIsReportedAsError() {
        int exit = run(dir.toString());

        assertThat(exit).isEqualTo(Main.SOME_FILES_FAILED);
        assertThat(stderr()).contains("could not index");
    }

    @Test
    void noArgumentsPrintsUsage() {
        int exit = run();

        assertThat(exit).isEqualTo(Main.USAGE);
        assertThat(stderr()).startsWith("Usage:");
        assertThat(stdout()).isEmpty();
    }

    @Test
    void invalidUtf8IsReportedAndOtherFilesStillIndexed() throws IOException {
        Path binary = Files.write(dir.resolve("binary.txt"), new byte[]{(byte) 0xC3, (byte) 0x28});
        Path good = Files.writeString(dir.resolve("good.txt"), "Hello");

        int exit = run(binary.toString(), good.toString());

        assertThat(exit).isEqualTo(Main.SOME_FILES_FAILED);
        assertThat(stderr()).contains("binary.txt", "not valid UTF-8");
        assertThat(stdout()).contains(good.toString());
    }

    @Test
    void buggyRuleIsReportedWithStackTraceAndOtherFilesStillIndexed() throws IOException {
        IndexingRule broken = new IndexingRule() {
            public String name() {
                return "Broken";
            }

            public RuleResult apply(List<String> words) {
                if (words.contains("boom")) {
                    throw new IllegalStateException("rule bug");
                }
                return new RuleResult.Count(words.size());
            }
        };
        Path bad = Files.writeString(dir.resolve("bad.txt"), "boom");
        Path good = Files.writeString(dir.resolve("good.txt"), "fine");

        int exit = Main.run(new String[]{bad.toString(), good.toString()}, List.of(broken), printTo(out), printTo(err));

        assertThat(exit).isEqualTo(Main.SOME_FILES_FAILED);
        assertThat(stderr()).contains("internal failure while indexing", "bad.txt", "IllegalStateException: rule bug");
        assertThat(stdout()).contains(good.toString(), "Broken: 1");
    }

    @Test
    void failingToWriteOutputIsAnError() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "Hello");
        var brokenPipe = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("broken pipe");
            }
        });

        int exit = Main.run(new String[]{file.toString()}, Rules.defaults(), brokenPipe, printTo(err));

        assertThat(exit).isEqualTo(Main.SOME_FILES_FAILED);
        assertThat(stderr()).contains("failed to write output");
    }

    private int run(String... args) {
        return Main.run(args, Rules.defaults(), printTo(out), printTo(err));
    }

    private static PrintStream printTo(ByteArrayOutputStream target) {
        return new PrintStream(target, true, StandardCharsets.UTF_8);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
