package io.github.bluething.indexer.rule;

import java.util.List;

public final class Rules {

    private Rules() {
    }

    /** The rules run for every file, in output order. Register new rules here. */
    public static List<IndexingRule> defaults() {
        return List.of(
                new UppercaseWordCountRule(),
                new LongWordsRule(5));
    }
}
