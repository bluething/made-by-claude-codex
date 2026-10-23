package io.github.bluething.indexer.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongWordsRuleTest {

    private final LongWordsRule rule = new LongWordsRule(5);

    @Test
    void fiveCharactersIsNotLongerThanFive() {
        assertThat(rule.apply(List.of("short", "longer", "sixsix")))
                .isEqualTo(new RuleResult.Words(List.of("longer", "sixsix")));
    }

    @Test
    void listsEachWordOnceInFirstSeenOrder() {
        assertThat(rule.apply(List.of("search", "system", "search", "indexing")))
                .isEqualTo(new RuleResult.Words(List.of("search", "system", "indexing")));
    }

    @Test
    void differentCasingIsADifferentWord() {
        assertThat(rule.apply(List.of("Search", "search")))
                .isEqualTo(new RuleResult.Words(List.of("Search", "search")));
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        // each mathematical bold letter is a surrogate pair: 5 code points, 10 chars
        String fiveCodePoints = "𝐇𝐞𝐥𝐥𝐨";

        assertThat(rule.apply(List.of(fiveCodePoints))).isEqualTo(new RuleResult.Words(List.of()));
    }

    @Test
    void nameReflectsThreshold() {
        assertThat(new LongWordsRule(8).name()).isEqualTo("Words longer than 8 characters");
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThatThrownBy(() -> new LongWordsRule(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
