package io.github.bluething.searchindexer.cli;

import io.github.bluething.searchindexer.indexing.BatchIndexer;
import io.github.bluething.searchindexer.indexing.DocumentIndexer;
import io.github.bluething.searchindexer.indexing.FileReport;
import io.github.bluething.searchindexer.indexing.ReportSink;
import io.github.bluething.searchindexer.report.ReportFormat;
import io.github.bluething.searchindexer.report.ReportWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * The command-line interface. Only translates between the command line and the indexing API:
 * parse options, run the batch, map the outcome to an exit code.
 */
@Command(
        name = "search-indexer",
        mixinStandardHelpOptions = true,
        versionProvider = ManifestVersionProvider.class,
        sortOptions = false,
        usageHelpAutoWidth = true,
        description = "Indexes text and HTML files and prints the result of every indexing rule for each file.",
        exitCodeOnInvalidInput = 2,
        exitCodeOnExecutionException = 3,
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                "0:All files were indexed.",
                "1:At least one file could not be indexed (the others were).",
                "2:Invalid command line.",
                "3:Run aborted, e.g. output could not be written."
        })
public final class IndexCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", paramLabel = "FILE",
            description = "Files to index. .html/.htm/.xhtml files are parsed as HTML; anything else is read as "
                    + "plain text. All files must be UTF-8.")
    private List<Path> files = List.of();

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private ReportFormat format = ReportFormat.TEXT;

    @Option(names = {"-p", "--parallelism"}, paramLabel = "N",
            description = "Number of files indexed concurrently, 1-" + BatchIndexer.MAX_PARALLELISM
                    + " (default: number of CPUs).")
    private int parallelism = Runtime.getRuntime().availableProcessors();

    @Spec
    private CommandSpec spec;

    private final DocumentIndexer indexer;

    public IndexCommand(DocumentIndexer indexer) {
        this.indexer = Objects.requireNonNull(indexer, "indexer");
    }

    /**
     * Builds the fully configured command line. Parser settings live here, next to the options that depend on
     * them (e.g. {@code --format jsonl}), so every caller gets the same behaviour.
     */
    public static CommandLine commandLine(DocumentIndexer indexer, PrintWriter out, PrintWriter err) {
        return new CommandLine(new IndexCommand(indexer))
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setOut(out)
                .setErr(err);
    }

    @Override
    public Integer call() {
        if (parallelism < 1 || parallelism > BatchIndexer.MAX_PARALLELISM) {
            throw new ParameterException(spec.commandLine(),
                    "--parallelism must be between 1 and " + BatchIndexer.MAX_PARALLELISM + ", was " + parallelism);
        }
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        // State for this invocation only, so a reused CommandLine starts clean every time.
        var printer = new CheckedPrinter(format.newWriter(out, err), out, err);

        try {
            new BatchIndexer(indexer, parallelism).indexAll(files, printer);
        } catch (IOException e) {
            err.println("search-indexer: aborted: could not write output: " + e.getMessage());
            return ExitCode.ABORTED.code();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("search-indexer: aborted: interrupted");
            return ExitCode.ABORTED.code();
        }
        return (printer.failedFiles == 0 ? ExitCode.SUCCESS : ExitCode.FILES_FAILED).code();
    }

    /** Writes each report, verifies it actually reached its destination, and counts failures. */
    private static final class CheckedPrinter implements ReportSink {

        private final ReportWriter writer;
        private final PrintWriter out;
        private final PrintWriter err;
        private int failedFiles;

        CheckedPrinter(ReportWriter writer, PrintWriter out, PrintWriter err) {
            this.writer = writer;
            this.out = out;
            this.err = err;
        }

        @Override
        public void accept(FileReport report) throws IOException {
            writer.write(report);
            // PrintWriter never throws; it records the error. Without these checks a closed pipe looks like
            // success. Both streams are checked because the text format writes failures to stderr.
            if (out.checkError() || err.checkError()) {
                throw new IOException("output stream is closed or failing");
            }
            if (report instanceof FileReport.Failed) {
                failedFiles++;
            }
        }
    }
}
