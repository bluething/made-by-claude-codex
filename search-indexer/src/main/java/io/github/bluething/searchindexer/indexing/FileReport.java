package io.github.bluething.searchindexer.indexing;

import io.github.bluething.searchindexer.rule.RuleResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of indexing one file. A failure is a value, not an exception: one bad file must not stop
 * a batch, and report writers decide how to present it.
 */
public sealed interface FileReport {

    Path file();

    record Indexed(Path file, List<RuleResult> results) implements FileReport {
        public Indexed {
            Objects.requireNonNull(file, "file");
            results = List.copyOf(results);
        }
    }

    record Failed(Path file, FailureReason reason, String detail) implements FileReport {
        public Failed {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
