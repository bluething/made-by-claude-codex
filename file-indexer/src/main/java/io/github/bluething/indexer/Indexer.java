package io.github.bluething.indexer;

import io.github.bluething.indexer.rule.IndexingRule;
import io.github.bluething.indexer.rule.RuleResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Runs every rule against one file. */
public final class Indexer {

    public record Report(Path file, List<Entry> entries) {
    }

    public record Entry(String ruleName, RuleResult result) {
    }

    private final List<IndexingRule> rules;

    public Indexer(List<IndexingRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Report index(Path file) throws IOException {
        List<String> words = Tokenizer.words(DocumentReader.read(file));
        List<Entry> entries = rules.stream()
                .map(rule -> new Entry(rule.name(), rule.apply(words)))
                .toList();
        return new Report(file, entries);
    }
}
