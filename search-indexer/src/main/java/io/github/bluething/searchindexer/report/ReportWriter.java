package io.github.bluething.searchindexer.report;

import io.github.bluething.searchindexer.indexing.FileReport;

import java.io.IOException;

/** Presents reports in some output format. Called from a single thread, in input order. */
public interface ReportWriter {

    void write(FileReport report) throws IOException;
}
