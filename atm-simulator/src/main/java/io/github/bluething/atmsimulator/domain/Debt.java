package io.github.bluething.atmsimulator.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code debtor} owes {@code creditor} a positive {@code amount}.
 *
 * <p>{@code sequence} records when the debt was opened and decides repayment order
 * (oldest first). Increasing an open debt keeps its sequence; a debt that is paid off
 * disappears, and if it is opened again later it gets a new, higher sequence.
 *
 * <p>Package private: outside the domain, debts are only visible as {@link DebtLine}s.
 */
record Debt(CustomerName debtor, CustomerName creditor, Money amount, long sequence) {

    Debt {
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(creditor, "creditor");
        Objects.requireNonNull(amount, "amount");
        if (debtor.equals(creditor)) {
            throw new IllegalArgumentException("A customer cannot owe themselves");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Debt must be positive, was " + amount);
        }
    }

    Debt increasedBy(Money extra) {
        return new Debt(debtor, creditor, amount.plus(extra), sequence);
    }

    /** @return the remaining debt, or empty if it is fully paid */
    Optional<Debt> reducedBy(Money paid) {
        Money remaining = amount.minus(paid);
        return remaining.isZero()
                ? Optional.empty()
                : Optional.of(new Debt(debtor, creditor, remaining, sequence));
    }
}
