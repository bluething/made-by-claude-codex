package io.github.bluething.indexer;

import io.github.bluething.indexer.rule.IndexingRule;
import io.github.bluething.indexer.rule.Rules;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.MalformedInputException;
import java.nio.file.AccessDeniedException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

public final class Main {

    static final int OK = 0;
    static final int SOME_FILES_FAILED = 1;
    static final int USAGE = 2;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, Rules.defaults(), System.out, System.err));
    }

    /** Separated from {@link #main} so it can be tested without exiting the JVM. */
    static int run(String[] args, List<IndexingRule> rules, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.println("Usage: java -jar file-indexer.jar <file> [<file> ...]");
            return USAGE;
        }

        var indexer = new Indexer(rules);
        int exitCode = OK;
        for (String arg : args) {
            // one bad file should not stop the others from being indexed
            try {
                print(indexer.index(Path.of(arg)), out);
            } catch (IOException | InvalidPathException e) {
                err.println("Error: could not index " + arg + ": " + describe(e));
                exitCode = SOME_FILES_FAILED;
            } catch (RuntimeException e) {
                // not the user's fault: most likely a bug in a rule, so keep the stack trace
                err.println("Error: internal failure while indexing " + arg);
                e.printStackTrace(err);
                exitCode = SOME_FILES_FAILED;
            }
        }
        // e.g. stdout piped to a closed or full destination; PrintStream swallows those errors
        if (out.checkError()) {
            err.println("Error: failed to write output");
            exitCode = SOME_FILES_FAILED;
        }
        return exitCode;
    }

    private static void print(Indexer.Report report, PrintStream out) {
        out.println(report.file());
        for (Indexer.Entry entry : report.entries()) {
            out.println("  " + entry.ruleName() + ": " + entry.result().render());
        }
    }

    private static String describe(Exception e) {
        return switch (e) {
            case NoSuchFileException _ -> "file not found";
            case AccessDeniedException _ -> "permission denied";
            case MalformedInputException _ -> "not valid UTF-8 text";
            case InvalidPathException ipe -> "invalid path (" + ipe.getReason() + ")";
            default -> e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
        };
    }
}
