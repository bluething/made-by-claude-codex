package io.github.bluething.searchindexer.rule;

import java.util.Objects;

/** The outcome of one rule for one document. */
public record RuleResult(String ruleId, String description, RuleValue value) {

    public RuleResult {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(value, "value");
    }
}
