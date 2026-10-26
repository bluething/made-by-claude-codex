package io.github.bluething.atmsimulator.domain;

/** Read-only queries on a {@link Ledger}. Handed to callers that must not change it. */
public interface LedgerView {

    boolean hasAccount(CustomerName customer);

    /** @throws UnknownCustomerException if the customer has no account */
    Money balanceOf(CustomerName customer);

    /** @throws UnknownCustomerException if the customer has no account */
    AccountStatement statementOf(CustomerName customer);
}
