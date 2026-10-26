package io.github.bluething.searchindexer.indexing;

import io.github.bluething.searchindexer.document.ContentExtractors;
import io.github.bluething.searchindexer.rule.IndexingRule;
import io.github.bluething.searchindexer.rule.IndexingRules;
import io.github.bluething.searchindexer.rule.RuleResult;
import io.github.bluething.searchindexer.rule.WordAccumulator;
import io.github.bluething.searchindexer.text.WordTokenizer;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Indexes one file: extract text, tokenize it once, feed every word to every rule.
 *
 * <p>Thread-safe, so one instance serves all worker threads: it has no mutable state of its own, and relies on its
 * collaborators' contracts ({@link IndexingRule}, {@code ContentExtractor} and {@link WordTokenizer} are all
 * required to be thread-safe; per-file state lives in fresh {@link WordAccumulator}s).
 */
public final class DocumentIndexer {

    private static final System.Logger LOG = System.getLogger(DocumentIndexer.class.getName());

    private final ContentExtractors extractors;
    private final WordTokenizer tokenizer;
    private final List<IndexingRule> rules;

    public DocumentIndexer(ContentExtractors extractors, WordTokenizer tokenizer, List<IndexingRule> rules) {
        this.extractors = Objects.requireNonNull(extractors, "extractors");
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
        this.rules = IndexingRules.validated(rules);
    }

    /** Never throws for a bad file; the problem is returned as {@link FileReport.Failed}. */
    public FileReport index(Path file) {
        try {
            return new FileReport.Indexed(file, evaluateRules(file));
        } catch (IOException e) {
            return new FileReport.Failed(file, FailureReason.of(e), detailOf(e));
        } catch (RuntimeException e) {
            // A bug, not bad input. Isolate it to this file, but keep the stack trace for whoever fixes it.
            LOG.log(System.Logger.Level.ERROR, "Unexpected failure while indexing " + file, e);
            return new FileReport.Failed(file, FailureReason.INTERNAL_ERROR, detailOf(e));
        }
    }

    private List<RuleResult> evaluateRules(Path file) throws IOException {
        requireRegularFile(file);

        List<WordAccumulator> accumulators = rules.stream().map(IndexingRule::newAccumulator).toList();
        try (Reader text = extractors.forFile(file).open(file)) {
            tokenizer.tokenize(text, word -> {
                for (WordAccumulator accumulator : accumulators) {
                    accumulator.accept(word);
                }
            });
        }

        var results = new ArrayList<RuleResult>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            IndexingRule rule = rules.get(i);
            results.add(new RuleResult(rule.id(), rule.description(), accumulators.get(i).result()));
        }
        return results;
    }

    private static void requireRegularFile(Path file) throws IOException {
        // Not Files.exists/isRegularFile: they return false on *any* error, so a permission problem
        // would be misreported as "not found". readAttributes throws the precise exception.
        if (!Files.readAttributes(file, BasicFileAttributes.class).isRegularFile()) {
            throw new NotRegularFileException(file);
        }
    }

    private static String detailOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
