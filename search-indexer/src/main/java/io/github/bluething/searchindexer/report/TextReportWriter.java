package io.github.bluething.searchindexer.report;

import io.github.bluething.searchindexer.indexing.FileReport;
import io.github.bluething.searchindexer.rule.RuleResult;
import io.github.bluething.searchindexer.rule.RuleValue;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * <pre>
 * notes.txt
 *   Words starting with an upper-case letter: 3
 *   Words longer than 5 characters: [Indexing, process]
 * </pre>
 * Failures are written to {@code err} as {@code search-indexer: missing.txt: file not found}.
 */
final class TextReportWriter implements ReportWriter {

    private final PrintWriter out;
    private final PrintWriter err;

    TextReportWriter(PrintWriter out, PrintWriter err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    @Override
    public void write(FileReport report) {
        switch (report) {
            case FileReport.Indexed indexed -> writeIndexed(indexed);
            case FileReport.Failed failed -> writeFailed(failed);
        }
    }

    private void writeIndexed(FileReport.Indexed report) {
        out.println(report.file());
        for (RuleResult result : report.results()) {
            out.println("  " + result.description() + ": " + render(result.value()));
        }
    }

    private void writeFailed(FileReport.Failed report) {
        String file = report.file().toString();
        // Exceptions such as NoSuchFileException use the path as their message; don't print it twice.
        String detail = report.detail().equals(file) ? "" : " (" + report.detail() + ")";
        err.println("search-indexer: " + file + ": " + report.reason().description() + detail);
    }

    private static String render(RuleValue value) {
        return switch (value) {
            case RuleValue.Count count -> Long.toString(count.value());
            case RuleValue.Terms terms -> terms.values()
                    + (terms.truncated() ? " (truncated to the first " + terms.values().size() + ")" : "");
        };
    }
}
