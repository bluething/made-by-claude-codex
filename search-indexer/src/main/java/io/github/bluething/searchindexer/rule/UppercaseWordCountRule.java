package io.github.bluething.searchindexer.rule;

/**
 * Counts words whose first character is an upper-case letter.
 * Every occurrence counts: "Java Java" is 2. Digits and scripts without case are never upper case.
 */
public final class UppercaseWordCountRule implements IndexingRule {

    @Override
    public String id() {
        return "uppercase-word-count";
    }

    @Override
    public String description() {
        return "Words starting with an upper-case letter";
    }

    @Override
    public WordAccumulator newAccumulator() {
        return new WordAccumulator() {
            private long count;

            @Override
            public void accept(String word) {
                if (!word.isEmpty() && Character.isUpperCase(word.codePointAt(0))) {
                    count++;
                }
            }

            @Override
            public RuleValue result() {
                return new RuleValue.Count(count);
            }
        };
    }
}
