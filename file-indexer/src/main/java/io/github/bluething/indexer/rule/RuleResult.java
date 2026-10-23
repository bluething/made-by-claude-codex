package io.github.bluething.indexer.rule;

import java.util.List;

/**
 * The outcome of a rule. Kept as data so tests (and a future JSON output) don't parse strings.
 * A rule that needs a different shape of output can define its own implementation.
 */
public interface RuleResult {

    String render();

    record Count(long value) implements RuleResult {
        @Override
        public String render() {
            return Long.toString(value);
        }
    }

    record Words(List<String> values) implements RuleResult {
        public Words {
            values = List.copyOf(values);
        }

        @Override
        public String render() {
            return values.toString();
        }
    }
}
