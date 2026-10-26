package io.github.bluething.searchindexer.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexingRulesTest {

    @Test
    void defaultsAreTheTwoRequiredRulesInOrder() {
        assertThat(IndexingRules.defaults())
                .extracting(IndexingRule::id)
                .containsExactly("uppercase-word-count", "long-words");
    }

    @Test
    void rejectsDuplicateIds() {
        assertThatThrownBy(() -> IndexingRules.validated(List.of(new LongWordsRule(5), new LongWordsRule(8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("long-words");
    }

    @Test
    void rejectsIdsThatAreNotKebabCase() {
        assertThatThrownBy(() -> IndexingRules.validated(List.of(ruleWithId("Long Words"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IndexingRules.validated(List.of(ruleWithId(null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static IndexingRule ruleWithId(String id) {
        return new IndexingRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String description() {
                return "test";
            }

            @Override
            public WordAccumulator newAccumulator() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
