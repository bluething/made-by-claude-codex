package io.github.bluething.searchindexer.indexing;

import io.github.bluething.searchindexer.document.ContentExtractor;
import io.github.bluething.searchindexer.document.ContentExtractors;
import io.github.bluething.searchindexer.rule.IndexingRule;
import io.github.bluething.searchindexer.rule.IndexingRules;
import io.github.bluething.searchindexer.rule.RuleResult;
import io.github.bluething.searchindexer.rule.RuleValue;
import io.github.bluething.searchindexer.rule.WordAccumulator;
import io.github.bluething.searchindexer.text.WordTokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocumentIndexerTest {

    @TempDir
    Path dir;

    private final DocumentIndexer indexer =
            new DocumentIndexer(ContentExtractors.defaults(), new WordTokenizer(), IndexingRules.defaults());

    @Test
    void appliesEveryRuleInRegistrationOrder() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "You are to create an Indexing process for a Search system.");

        assertThat(indexer.index(file)).isEqualTo(new FileReport.Indexed(file, List.of(
                new RuleResult("uppercase-word-count", "Words starting with an upper-case letter",
                        new RuleValue.Count(3)),
                new RuleResult("long-words", "Words longer than 5 characters",
                        new RuleValue.Terms(List.of("create", "Indexing", "process", "Search", "system"), false)))));
    }

    @Test
    void htmlIsIndexedByItsTextContent() throws IOException {
        Path file = Files.writeString(dir.resolve("a.html"),
                "<html><body class=\"Classname\"><h1>Java Engineer</h1><script>Hidden()</script></body></html>");

        var report = (FileReport.Indexed) indexer.index(file);

        assertThat(report.results().getFirst().value()).isEqualTo(new RuleValue.Count(2));
    }

    @Test
    void missingFile() {
        Path missing = dir.resolve("missing.txt");

        assertThat(indexer.index(missing)).isEqualTo(
                new FileReport.Failed(missing, FailureReason.NOT_FOUND, missing.toString()));
    }

    @Test
    void directoryIsNotARegularFile() {
        assertThat(indexer.index(dir))
                .isInstanceOfSatisfying(FileReport.Failed.class,
                        failed -> assertThat(failed.reason()).isEqualTo(FailureReason.NOT_A_REGULAR_FILE));
    }

    @Test
    void unreadableDirectoryIsAccessDeniedNotNotFound() throws IOException {
        Path locked = Files.createDirectory(dir.resolve("locked"));
        Path file = Files.writeString(locked.resolve("a.txt"), "secret");
        assumeTrue(locked.getFileSystem().supportedFileAttributeViews().contains("posix"), "POSIX permissions");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            assumeFalse(Files.isReadable(file), "running as a user that ignores permissions (e.g. root)");

            assertThat(indexer.index(file))
                    .isInstanceOfSatisfying(FileReport.Failed.class,
                            failed -> assertThat(failed.reason()).isEqualTo(FailureReason.ACCESS_DENIED));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void malformedEncodingFoundMidFileFailsTheWholeFile() throws IOException {
        byte[] content = ("Valid ".repeat(5_000) + "ÿ").getBytes(StandardCharsets.ISO_8859_1);
        Path file = Files.write(dir.resolve("latin1.txt"), content);

        assertThat(indexer.index(file))
                .isInstanceOfSatisfying(FileReport.Failed.class,
                        failed -> assertThat(failed.reason()).isEqualTo(FailureReason.MALFORMED_ENCODING));
    }

    @Test
    void ruleBugBecomesInternalErrorAndTheReaderIsStillClosed() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "boom");
        var closed = new AtomicBoolean();
        ContentExtractor tracking = new ContentExtractor() {
            @Override
            public boolean supports(Path ignored) {
                return true;
            }

            @Override
            public Reader open(Path ignored) {
                return new StringReader("boom") {
                    @Override
                    public void close() {
                        closed.set(true);
                        super.close();
                    }
                };
            }
        };
        var indexerWithBrokenRule = new DocumentIndexer(
                new ContentExtractors(List.of(), tracking), new WordTokenizer(), List.of(new ThrowingRule()));

        assertThat(indexerWithBrokenRule.index(file)).isEqualTo(
                new FileReport.Failed(file, FailureReason.INTERNAL_ERROR, "rule bug"));
        assertThat(closed).isTrue();
    }

    @Test
    void failureReasonClassifiesUnknownIoErrors() {
        assertThat(FailureReason.of(new IOException("disk on fire"))).isEqualTo(FailureReason.IO_ERROR);
        assertThat(FailureReason.of(new java.nio.file.AccessDeniedException("x")))
                .isEqualTo(FailureReason.ACCESS_DENIED);
    }

    private static final class ThrowingRule implements IndexingRule {
        @Override
        public String id() {
            return "throwing";
        }

        @Override
        public String description() {
            return "Throws";
        }

        @Override
        public WordAccumulator newAccumulator() {
            return new WordAccumulator() {
                @Override
                public void accept(String word) {
                    throw new IllegalStateException("rule bug");
                }

                @Override
                public RuleValue result() {
                    return new RuleValue.Count(0);
                }
            };
        }
    }
}
