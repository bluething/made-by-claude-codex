package io.github.bluething.atmsimulator.domain;

import java.util.List;
import java.util.Objects;

/**
 * A customer's position at one point in time.
 *
 * @param owedTo   what this customer owes others, oldest first
 * @param owedFrom what others owe this customer, oldest first
 */
public record AccountStatement(
        CustomerName customer,
        Money balance,
        List<DebtLine> owedTo,
        List<DebtLine> owedFrom) {

    public AccountStatement {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(balance, "balance");
        // Records are only shallowly immutable; copy so callers can't change the statement.
        owedTo = List.copyOf(owedTo);
        owedFrom = List.copyOf(owedFrom);
    }
}
