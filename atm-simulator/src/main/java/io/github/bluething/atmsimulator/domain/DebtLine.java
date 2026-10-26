package io.github.bluething.atmsimulator.domain;

import java.util.Objects;

/** One line of a statement: an amount owed to, or owed by, {@code counterparty}. */
public record DebtLine(CustomerName counterparty, Money amount) {

    public DebtLine {
        Objects.requireNonNull(counterparty, "counterparty");
        Objects.requireNonNull(amount, "amount");
    }
}
