package io.github.bluething.searchindexer.indexing;

import java.io.IOException;

/** Receives reports in input order. May throw if the destination (e.g. stdout) fails. */
@FunctionalInterface
public interface ReportSink {

    void accept(FileReport report) throws IOException;
}
