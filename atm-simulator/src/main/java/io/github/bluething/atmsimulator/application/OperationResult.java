package io.github.bluething.atmsimulator.application;

import io.github.bluething.atmsimulator.domain.AccountStatement;
import io.github.bluething.atmsimulator.domain.Payment;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of a money operation.
 *
 * @param payments  money that moved because of the operation, in order
 * @param statement the customer's position right after the operation, taken from the
 *                  same transaction, so it can't include anybody else's later changes
 */
public record OperationResult(List<Payment> payments, AccountStatement statement) {

    public OperationResult {
        payments = List.copyOf(payments);
        Objects.requireNonNull(statement, "statement");
    }
}
