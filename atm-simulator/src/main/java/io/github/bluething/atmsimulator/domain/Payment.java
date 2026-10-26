package io.github.bluething.atmsimulator.domain;

import java.util.Objects;

/** Money that actually moved between two accounts. Cancelled debt is not a payment. */
public record Payment(CustomerName from, CustomerName to, Money amount) {

    public Payment {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Payment must be positive, was " + amount);
        }
    }
}
