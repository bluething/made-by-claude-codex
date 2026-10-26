package io.github.bluething.atmsimulator.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtTest {

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");

    @Test
    void increaseKeepsTheSequence() {
        Debt debt = new Debt(BOB, ALICE, Money.of(10), 7);

        assertThat(debt.increasedBy(Money.of(5))).isEqualTo(new Debt(BOB, ALICE, Money.of(15), 7));
    }

    @Test
    void partialReductionKeepsTheRest() {
        Debt debt = new Debt(BOB, ALICE, Money.of(10), 7);

        assertThat(debt.reducedBy(Money.of(4))).contains(new Debt(BOB, ALICE, Money.of(6), 7));
    }

    @Test
    void fullReductionMeansTheDebtIsGone() {
        Debt debt = new Debt(BOB, ALICE, Money.of(10), 7);

        assertThat(debt.reducedBy(Money.of(10))).isEmpty();
    }

    @Test
    void rejectsZeroOrSelfDebt() {
        assertThatThrownBy(() -> new Debt(BOB, ALICE, Money.ZERO, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Debt(BOB, BOB, Money.of(1), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
