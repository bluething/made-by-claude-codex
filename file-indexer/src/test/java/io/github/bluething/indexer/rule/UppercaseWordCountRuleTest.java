package io.github.bluething.indexer.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UppercaseWordCountRuleTest {

    private final UppercaseWordCountRule rule = new UppercaseWordCountRule();

    @Test
    void countsEveryOccurrenceNotDistinctWords() {
        assertThat(rule.apply(List.of("Java", "is", "Java", "and", "Maven")))
                .isEqualTo(new RuleResult.Count(3));
    }

    @Test
    void onlyLooksAtTheFirstCharacter() {
        assertThat(rule.apply(List.of("iPhone", "McDonald", "NASA", "eBay")))
                .isEqualTo(new RuleResult.Count(2));
    }

    @Test
    void digitsAndNonCasedScriptsAreNotUpperCase() {
        assertThat(rule.apply(List.of("2024", "東京", "Élan")))
                .isEqualTo(new RuleResult.Count(1));
    }

    @Test
    void emptyInputCountsZero() {
        assertThat(rule.apply(List.of())).isEqualTo(new RuleResult.Count(0));
    }
}
