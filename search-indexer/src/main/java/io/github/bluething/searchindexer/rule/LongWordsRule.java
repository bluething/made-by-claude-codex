package io.github.bluething.searchindexer.rule;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

/**
 * Lists the words longer than a threshold.
 *
 * <ul>
 *   <li>Length is measured in Unicode code points of the NFC-normalised word, so {@code café} is 4 whether or not
 *       its accent was typed as a separate combining character. Inner apostrophes and hyphens count.</li>
 *   <li>Each distinct word is listed once, in order of first appearance, with its original casing:
 *       {@code Search} and {@code search} are different terms to a case-sensitive index.</li>
 *   <li>At most {@code maxTerms} distinct words are kept, which bounds memory on hostile input. Hitting the limit
 *       is reported via {@link RuleValue.Terms#truncated()}.</li>
 * </ul>
 */
public final class LongWordsRule implements IndexingRule {

    public static final int DEFAULT_MAX_TERMS = 100_000;

    private final int threshold;
    private final int maxTerms;

    /** @param threshold words must be strictly longer than this many characters */
    public LongWordsRule(int threshold) {
        this(threshold, DEFAULT_MAX_TERMS);
    }

    public LongWordsRule(int threshold, int maxTerms) {
        if (threshold < 0) {
            throw new IllegalArgumentException("threshold must not be negative, was " + threshold);
        }
        if (maxTerms < 1) {
            throw new IllegalArgumentException("maxTerms must be positive, was " + maxTerms);
        }
        this.threshold = threshold;
        this.maxTerms = maxTerms;
    }

    @Override
    public String id() {
        return "long-words";
    }

    @Override
    public String description() {
        return "Words longer than " + threshold + " characters";
    }

    @Override
    public WordAccumulator newAccumulator() {
        return new WordAccumulator() {
            private final SequencedSet<String> words = new LinkedHashSet<>();
            private boolean truncated;

            @Override
            public void accept(String word) {
                if (!isLong(word) || words.contains(word)) {
                    return;
                }
                if (words.size() < maxTerms) {
                    words.add(word);
                } else {
                    truncated = true;
                }
            }

            @Override
            public RuleValue result() {
                return new RuleValue.Terms(List.copyOf(words), truncated);
            }
        };
    }

    private boolean isLong(String word) {
        // Cheap check first: a string never has more code points than chars.
        return word.length() > threshold && word.codePointCount(0, word.length()) > threshold;
    }
}
