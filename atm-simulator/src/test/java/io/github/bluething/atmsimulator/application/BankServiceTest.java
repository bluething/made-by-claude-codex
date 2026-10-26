package io.github.bluething.atmsimulator.application;

import io.github.bluething.atmsimulator.domain.AccountStatement;
import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.DebtLine;
import io.github.bluething.atmsimulator.domain.InsufficientFundsException;
import io.github.bluething.atmsimulator.domain.Money;
import io.github.bluething.atmsimulator.domain.Payment;
import io.github.bluething.atmsimulator.infrastructure.InMemoryLedgerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses the real in-memory store: it is fast, and a fake would only re-implement it. */
class BankServiceTest {

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");

    private BankService bank;

    @BeforeEach
    void setUp() {
        bank = new BankService(new InMemoryLedgerStore());
        bank.openOrGetAccount(ALICE);
        bank.openOrGetAccount(BOB);
    }

    @Test
    void openingTwiceReturnsTheExistingAccount() {
        bank.deposit(ALICE, Money.of(10));

        AccountStatement statement = bank.openOrGetAccount(ALICE);

        assertThat(statement.balance()).isEqualTo(Money.of(10));
    }

    @Test
    void depositReturnsRepaymentsAndTheResultingStatement() {
        bank.transfer(BOB, ALICE, Money.of(40));

        OperationResult result = bank.deposit(BOB, Money.of(30));

        assertThat(result.payments()).containsExactly(new Payment(BOB, ALICE, Money.of(30)));
        assertThat(result.statement()).isEqualTo(new AccountStatement(
                BOB, Money.ZERO, List.of(new DebtLine(ALICE, Money.of(10))), List.of()));
    }

    @Test
    void withdrawReturnsTheResultingStatement() {
        bank.deposit(ALICE, Money.of(10));

        OperationResult result = bank.withdraw(ALICE, Money.of(4));

        assertThat(result.payments()).isEmpty();
        assertThat(result.statement().balance()).isEqualTo(Money.of(6));
    }

    @Test
    void transferReturnsTheSendersStatement() {
        bank.deposit(BOB, Money.of(80));

        OperationResult result = bank.transfer(BOB, ALICE, Money.of(50));

        assertThat(result.payments()).containsExactly(new Payment(BOB, ALICE, Money.of(50)));
        assertThat(result.statement().customer()).isEqualTo(BOB);
        assertThat(result.statement().balance()).isEqualTo(Money.of(30));
        assertThat(bank.statementOf(ALICE).balance()).isEqualTo(Money.of(50));
    }

    @Test
    void rejectedOperationChangesNothing() {
        bank.deposit(ALICE, Money.of(10));

        assertThatThrownBy(() -> bank.withdraw(ALICE, Money.of(11)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(bank.statementOf(ALICE).balance()).isEqualTo(Money.of(10));
    }
}
