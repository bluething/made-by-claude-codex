package io.github.bluething.searchindexer.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UppercaseWordCountRuleTest {

    private final UppercaseWordCountRule rule = new UppercaseWordCountRule();

    @Test
    void countsEveryOccurrenceNotDistinctWords() {
        assertThat(apply("Java", "is", "Java", "and", "Maven")).isEqualTo(new RuleValue.Count(3));
    }

    @Test
    void onlyTheFirstCharacterMatters() {
        assertThat(apply("iPhone", "McDonald", "NASA", "eBay")).isEqualTo(new RuleValue.Count(2));
    }

    @Test
    void digitsAndUncasedScriptsAreNotUpperCase() {
        assertThat(apply("2025", "東京", "Élan", "𝐇𝐞𝐥𝐥𝐨")).isEqualTo(new RuleValue.Count(2));
    }

    @Test
    void emptyDocumentCountsZero() {
        assertThat(apply()).isEqualTo(new RuleValue.Count(0));
    }

    @Test
    void accumulatorsAreIndependent() {
        WordAccumulator first = rule.newAccumulator();
        WordAccumulator second = rule.newAccumulator();

        first.accept("One");

        assertThat(second.result()).isEqualTo(new RuleValue.Count(0));
    }

    private RuleValue apply(String... words) {
        WordAccumulator accumulator = rule.newAccumulator();
        List.of(words).forEach(accumulator::accept);
        return accumulator.result();
    }
}
