package io.github.bluething.atmsimulator.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTest {

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");

    private Ledger ledger;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        ledger.openAccount(ALICE);
        ledger.openAccount(BOB);
    }

    private static Money usd(long dollars) {
        return Money.of(dollars);
    }

    @Nested
    class Accounts {

        @Test
        void newAccountStartsEmpty() {
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(Money.ZERO);
            assertThat(ledger.statementOf(ALICE))
                    .isEqualTo(new AccountStatement(ALICE, Money.ZERO, List.of(), List.of()));
        }

        @Test
        void openingAnExistingAccountKeepsItsBalance() {
            ledger.deposit(ALICE, usd(10));

            assertThat(ledger.openAccount(ALICE)).isFalse();
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(10));
        }

        @Test
        void unknownCustomerIsRejected() {
            CustomerName ghost = CustomerName.of("Ghost");

            assertThat(ledger.hasAccount(ghost)).isFalse();
            assertThatThrownBy(() -> ledger.balanceOf(ghost)).isInstanceOf(UnknownCustomerException.class);
            assertThatThrownBy(() -> ledger.deposit(ghost, usd(1))).isInstanceOf(UnknownCustomerException.class);
        }

        @Test
        void copyIsIndependent() {
            Ledger copy = ledger.copy();
            copy.deposit(ALICE, usd(10));

            assertThat(ledger.balanceOf(ALICE)).isEqualTo(Money.ZERO);
            assertThat(copy.balanceOf(ALICE)).isEqualTo(usd(10));
        }
    }

    @Nested
    class DepositAndWithdraw {

        @Test
        void depositAddsToBalance() {
            ledger.deposit(ALICE, usd(100));
            ledger.deposit(ALICE, Money.of("0.50"));

            assertThat(ledger.balanceOf(ALICE)).isEqualTo(Money.of("100.50"));
        }

        @Test
        void withdrawSubtractsFromBalance() {
            ledger.deposit(ALICE, usd(100));
            ledger.withdraw(ALICE, usd(100));

            assertThat(ledger.balanceOf(ALICE)).isEqualTo(Money.ZERO);
        }

        @Test
        void cannotWithdrawMoreThanBalance() {
            ledger.deposit(ALICE, usd(10));

            assertThatThrownBy(() -> ledger.withdraw(ALICE, Money.of("10.01")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(10));
        }

        @Test
        void amountsMustBePositive() {
            assertThatThrownBy(() -> ledger.deposit(ALICE, Money.ZERO)).isInstanceOf(InvalidAmountException.class);
            assertThatThrownBy(() -> ledger.withdraw(ALICE, Money.ZERO)).isInstanceOf(InvalidAmountException.class);
        }
    }
}
