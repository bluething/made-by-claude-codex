package io.github.bluething.searchindexer;

import io.github.bluething.searchindexer.cli.IndexCommand;
import io.github.bluething.searchindexer.document.ContentExtractors;
import io.github.bluething.searchindexer.indexing.DocumentIndexer;
import io.github.bluething.searchindexer.rule.IndexingRules;
import io.github.bluething.searchindexer.text.WordTokenizer;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Composition root: the only place that knows the concrete classes and wires them together.
 * Everything else receives its collaborators through constructors, which keeps it testable without a
 * dependency-injection framework.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Fixed to UTF-8 rather than the platform default, so output is identical on every machine.
        var out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        var err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        System.exit(execute(args, out, err));
    }

    static int execute(String[] args, PrintWriter out, PrintWriter err) {
        var indexer = new DocumentIndexer(
                ContentExtractors.defaults(),
                new WordTokenizer(),
                IndexingRules.defaults());

        return IndexCommand.commandLine(indexer, out, err).execute(args);
    }
}
