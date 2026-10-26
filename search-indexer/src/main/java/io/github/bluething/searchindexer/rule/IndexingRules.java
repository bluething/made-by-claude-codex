package io.github.bluething.searchindexer.rule;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** The single place where the application's rules are registered. */
public final class IndexingRules {

    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private IndexingRules() {
    }

    /** Rules applied to every document, in output order. */
    public static List<IndexingRule> defaults() {
        return validated(List.of(
                new UppercaseWordCountRule(),
                new LongWordsRule(5)));
    }

    /**
     * Checks the rule set at start-up, so a misconfigured rule fails fast instead of producing ambiguous output.
     *
     * @throws IllegalArgumentException if an id is not kebab-case or two rules share an id
     */
    public static List<IndexingRule> validated(List<IndexingRule> rules) {
        var ids = new HashSet<String>();
        for (IndexingRule rule : rules) {
            if (rule.id() == null || !RULE_ID.matcher(rule.id()).matches()) {
                throw new IllegalArgumentException("Rule id must be kebab-case, was: " + rule.id());
            }
            if (!ids.add(rule.id())) {
                throw new IllegalArgumentException("Duplicate rule id: " + rule.id());
            }
        }
        return List.copyOf(rules);
    }
}
