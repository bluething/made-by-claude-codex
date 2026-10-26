package io.github.bluething.searchindexer.report;

import java.io.PrintWriter;

/** The output formats the CLI offers. Adding one = a new {@link ReportWriter} plus a constant here. */
public enum ReportFormat {

    /** Human-readable; failures go to stderr so stdout contains only results. */
    TEXT {
        @Override
        public ReportWriter newWriter(PrintWriter out, PrintWriter err) {
            return new TextReportWriter(out, err);
        }
    },

    /** One JSON object per line on stdout, failures included, for downstream pipelines. */
    JSONL {
        @Override
        public ReportWriter newWriter(PrintWriter out, PrintWriter err) {
            return new JsonLinesReportWriter(out);
        }
    };

    public abstract ReportWriter newWriter(PrintWriter out, PrintWriter err);
}
