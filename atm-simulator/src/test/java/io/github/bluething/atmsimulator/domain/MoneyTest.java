package io.github.bluething.atmsimulator.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Nested
    class Construction {

        @Test
        void normalisesScaleSoEqualAmountsAreEqual() {
            assertThat(Money.of("5")).isEqualTo(Money.of("5.0")).isEqualTo(Money.of("5.00"));
            assertThat(Money.of("5").hashCode()).isEqualTo(Money.of("5.00").hashCode());
        }

        @Test
        void rejectsNegativeAmounts() {
            assertThatThrownBy(() -> new Money(new BigDecimal("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsSubCentPrecisionInsteadOfRounding() {
            assertThatThrownBy(() -> Money.of("1.005"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void acceptsTrailingZerosBeyondTwoDecimals() {
            assertThat(Money.of("1.500")).isEqualTo(Money.of("1.5"));
        }
    }

    @Nested
    class ParsePositive {

        @ParameterizedTest
        @CsvSource({"100, 100.00", "12.5, 12.50", "0.01, 0.01", "999999999999.99, 999999999999.99"})
        void parsesValidInput(String input, String expected) {
            assertThat(Money.parsePositive(input)).isEqualTo(Money.of(expected));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "abc", "-5", "+5", "1.234", "1e3", " 5", "1,000", "1.", ".5",
                "1000000000000"})
        void rejectsMalformedInput(String input) {
            assertThatThrownBy(() -> Money.parsePositive(input))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00"})
        void rejectsZero(String input) {
            assertThatThrownBy(() -> Money.parsePositive(input))
                    .isInstanceOf(InvalidAmountException.class)
                    .hasMessageContaining("greater than $0");
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void addsAndSubtracts() {
            assertThat(Money.of("10.25").plus(Money.of("0.75"))).isEqualTo(Money.of(11));
            assertThat(Money.of(11).minus(Money.of("0.75"))).isEqualTo(Money.of("10.25"));
        }

        @Test
        void subtractingMoreThanAvailableIsABug() {
            assertThatThrownBy(() -> Money.of(1).minus(Money.of(2)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void minAndComparisons() {
            Money small = Money.of(1);
            Money large = Money.of(2);

            assertThat(small.min(large)).isEqualTo(small);
            assertThat(large.min(small)).isEqualTo(small);
            assertThat(small.isLessThan(large)).isTrue();
            assertThat(large.isLessThan(small)).isFalse();
            assertThat(Money.ZERO.isZero()).isTrue();
            assertThat(Money.ZERO.isPositive()).isFalse();
            assertThat(small.isPositive()).isTrue();
        }
    }

    @Test
    void printsPlainAmount() {
        assertThat(Money.of("1e2")).hasToString("100.00");
    }
}
