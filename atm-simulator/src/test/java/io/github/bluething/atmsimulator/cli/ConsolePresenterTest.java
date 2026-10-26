package io.github.bluething.atmsimulator.cli;

import io.github.bluething.atmsimulator.application.OperationResult;
import io.github.bluething.atmsimulator.domain.AccountStatement;
import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.DebtLine;
import io.github.bluething.atmsimulator.domain.Money;
import io.github.bluething.atmsimulator.domain.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolePresenterTest {

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");
    private static final CustomerName CAROL = CustomerName.of("Carol");

    private final ConsolePresenter presenter = new ConsolePresenter();

    @ParameterizedTest
    @CsvSource({"0, $0", "100, $100", "100.5, $100.50", "0.01, $0.01", "1234567, $1234567"})
    void formatsMoney(String amount, String expected) {
        assertThat(ConsolePresenter.format(Money.of(amount))).isEqualTo(expected);
    }

    @Test
    void greetsWithFullStatement() {
        AccountStatement statement = new AccountStatement(ALICE, Money.of(210),
                List.of(new DebtLine(CAROL, Money.of(5))),
                List.of(new DebtLine(BOB, Money.of(40))));

        assertThat(presenter.loggedIn(statement)).containsExactly(
                "Hello, Alice!",
                "Your balance is $210",
                "Owed $5 to Carol",
                "Owed $40 from Bob");
    }

    @Test
    void showsPaymentsBeforeTheStatement() {
        OperationResult result = new OperationResult(
                List.of(new Payment(BOB, ALICE, Money.of(30))),
                new AccountStatement(BOB, Money.ZERO, List.of(new DebtLine(ALICE, Money.of(70))), List.of()));

        assertThat(presenter.operation(result)).containsExactly(
                "Transferred $30 to Alice",
                "Your balance is $0",
                "Owed $70 to Alice");
    }

    @Test
    void saysGoodbye() {
        assertThat(presenter.loggedOut(ALICE)).containsExactly("Goodbye, Alice!");
    }

    @Test
    void helpListsEveryCommand() {
        assertThat(presenter.help())
                .hasSize(CommandSyntax.values().length + 1)
                .anySatisfy(line -> assertThat(line).contains("transfer [target] [amount]"));
    }
}
