package io.github.bluething.indexer.rule;

import java.util.List;

/** Counts every occurrence of a word whose first character is an upper-case letter. */
public final class UppercaseWordCountRule implements IndexingRule {

    @Override
    public String name() {
        return "Words starting with an upper-case letter";
    }

    @Override
    public RuleResult apply(List<String> words) {
        long count = words.stream()
                .filter(word -> Character.isUpperCase(word.codePointAt(0)))
                .count();
        return new RuleResult.Count(count);
    }
}
