package io.github.bluething.indexer.rule;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Lists words longer than a given number of characters.
 * Each distinct word is listed once, in the order it first appears, with its original casing.
 */
public final class LongWordsRule implements IndexingRule {

    private final int minLengthExclusive;

    public LongWordsRule(int minLengthExclusive) {
        if (minLengthExclusive < 0) {
            throw new IllegalArgumentException("minLengthExclusive must be >= 0, was " + minLengthExclusive);
        }
        this.minLengthExclusive = minLengthExclusive;
    }

    @Override
    public String name() {
        return "Words longer than " + minLengthExclusive + " characters";
    }

    @Override
    public RuleResult apply(List<String> words) {
        var longWords = new LinkedHashSet<String>();
        for (String word : words) {
            // code points, not chars, so emoji/supplementary characters count as one
            if (word.codePointCount(0, word.length()) > minLengthExclusive) {
                longWords.add(word);
            }
        }
        return new RuleResult.Words(List.copyOf(longWords));
    }
}
