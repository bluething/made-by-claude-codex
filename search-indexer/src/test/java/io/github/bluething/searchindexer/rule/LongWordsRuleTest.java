package io.github.bluething.searchindexer.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongWordsRuleTest {

    private final LongWordsRule rule = new LongWordsRule(5);

    @Test
    void exactlyFiveCharactersIsNotLongerThanFive() {
        assertThat(apply(rule, "short", "longer")).isEqualTo(terms("longer"));
    }

    @Test
    void listsEachWordOnceInFirstSeenOrder() {
        assertThat(apply(rule, "search", "system", "search", "indexing"))
                .isEqualTo(terms("search", "system", "indexing"));
    }

    @Test
    void casingMakesADifferentTerm() {
        assertThat(apply(rule, "Search", "search")).isEqualTo(terms("Search", "search"));
    }

    @Test
    void measuresCodePointsNotUtf16Units() {
        String fiveCodePoints = "𝐇𝐞𝐥𝐥𝐨"; // 10 chars

        assertThat(apply(rule, fiveCodePoints)).isEqualTo(terms());
    }

    @Test
    void stopsAtMaxTermsAndSaysSo() {
        var capped = new LongWordsRule(5, 2);

        assertThat(apply(capped, "alpha1", "alpha2", "alpha1", "alpha3"))
                .isEqualTo(new RuleValue.Terms(List.of("alpha1", "alpha2"), true));
    }

    @Test
    void repeatingAKnownTermAtTheCapIsNotTruncation() {
        var capped = new LongWordsRule(5, 1);

        assertThat(apply(capped, "alpha1", "alpha1")).isEqualTo(terms("alpha1"));
    }

    @Test
    void describesItsThreshold() {
        assertThat(new LongWordsRule(8).description()).isEqualTo("Words longer than 8 characters");
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new LongWordsRule(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LongWordsRule(5, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static RuleValue apply(IndexingRule rule, String... words) {
        WordAccumulator accumulator = rule.newAccumulator();
        List.of(words).forEach(accumulator::accept);
        return accumulator.result();
    }

    private static RuleValue terms(String... values) {
        return new RuleValue.Terms(List.of(values), false);
    }
}
