package io.github.bluething.searchindexer.report;

import io.github.bluething.searchindexer.indexing.FailureReason;
import io.github.bluething.searchindexer.indexing.FileReport;
import io.github.bluething.searchindexer.rule.RuleResult;
import io.github.bluething.searchindexer.rule.RuleValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextReportWriterTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final ReportWriter writer = ReportFormat.TEXT.newWriter(new PrintWriter(out), new PrintWriter(err));

    @Test
    void writesOneLinePerRuleUnderTheFileName() throws IOException {
        writer.write(new FileReport.Indexed(Path.of("a.txt"), List.of(
                new RuleResult("count", "Capitalised", new RuleValue.Count(2)),
                new RuleResult("terms", "Long", new RuleValue.Terms(List.of("Indexing", "process"), false)))));

        assertThat(out.toString()).isEqualToNormalizingNewlines("""
                a.txt
                  Capitalised: 2
                  Long: [Indexing, process]
                """);
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void marksTruncatedTermLists() throws IOException {
        writer.write(new FileReport.Indexed(Path.of("a.txt"), List.of(
                new RuleResult("terms", "Long", new RuleValue.Terms(List.of("alpha1"), true)))));

        assertThat(out.toString()).contains("Long: [alpha1] (truncated to the first 1)");
    }

    @Test
    void writesFailuresToErrWithoutRepeatingThePath() throws IOException {
        writer.write(new FileReport.Failed(Path.of("missing.txt"), FailureReason.NOT_FOUND, "missing.txt"));
        writer.write(new FileReport.Failed(Path.of("a.txt"), FailureReason.IO_ERROR, "disk on fire"));

        assertThat(err.toString()).isEqualToNormalizingNewlines("""
                search-indexer: missing.txt: file not found
                search-indexer: a.txt: I/O error (disk on fire)
                """);
        assertThat(out.toString()).isEmpty();
    }
}
