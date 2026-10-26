package io.github.bluething.atmsimulator.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"Alice", "bob_2", "mary-jane", "X"})
    void acceptsValidNames(String raw) {
        assertThat(CustomerName.of(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "dollar$", "émile"})
    void rejectsInvalidCharactersOrEmpty(String raw) {
        assertThatThrownBy(() -> CustomerName.of(raw))
                .isInstanceOf(InvalidCustomerNameException.class);
    }

    @Test
    void rejectsNamesLongerThanTheLimit() {
        String tooLong = "a".repeat(CustomerName.MAX_LENGTH + 1);

        assertThatThrownBy(() -> CustomerName.of(tooLong))
                .isInstanceOf(InvalidCustomerNameException.class)
                .hasMessageContaining("between 1 and " + CustomerName.MAX_LENGTH);
    }

    @Test
    void isCaseSensitive() {
        assertThat(CustomerName.of("alice")).isNotEqualTo(CustomerName.of("Alice"));
    }

    @Test
    void printsAsPlainValue() {
        assertThat(CustomerName.of("Alice")).hasToString("Alice");
    }
}
