package io.github.bluething.atmsimulator.cli;

import io.github.bluething.atmsimulator.application.OperationResult;
import io.github.bluething.atmsimulator.domain.AccountStatement;
import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.DebtLine;
import io.github.bluething.atmsimulator.domain.Money;
import io.github.bluething.atmsimulator.domain.Payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns results into the text the customer sees. Pure functions with no I/O, so the
 * exact wording is easy to test and to change in one place.
 */
public final class ConsolePresenter {

    public List<String> loggedIn(AccountStatement statement) {
        List<String> lines = new ArrayList<>();
        lines.add("Hello, " + statement.customer() + "!");
        lines.addAll(statement(statement));
        return lines;
    }

    public List<String> operation(OperationResult result) {
        List<String> lines = new ArrayList<>();
        for (Payment payment : result.payments()) {
            lines.add("Transferred " + format(payment.amount()) + " to " + payment.to());
        }
        lines.addAll(statement(result.statement()));
        return lines;
    }

    public List<String> loggedOut(CustomerName customer) {
        return List.of("Goodbye, " + customer + "!");
    }

    public List<String> help() {
        List<String> lines = new ArrayList<>();
        lines.add("Commands:");
        for (CommandSyntax syntax : CommandSyntax.values()) {
            lines.add("  %-28s %s".formatted(syntax.usage(), syntax.description()));
        }
        return lines;
    }

    private static List<String> statement(AccountStatement statement) {
        List<String> lines = new ArrayList<>();
        lines.add("Your balance is " + format(statement.balance()));
        for (DebtLine debt : statement.owedTo()) {
            lines.add("Owed " + format(debt.amount()) + " to " + debt.counterparty());
        }
        for (DebtLine debt : statement.owedFrom()) {
            lines.add("Owed " + format(debt.amount()) + " from " + debt.counterparty());
        }
        return lines;
    }

    /** {@code $100} for whole dollars, as in the specification; {@code $100.50} otherwise. */
    static String format(Money money) {
        BigDecimal amount = money.amount();
        boolean wholeDollars = amount.remainder(BigDecimal.ONE).signum() == 0;
        return "$" + (wholeDollars ? amount.toBigInteger().toString() : amount.toPlainString());
    }
}
