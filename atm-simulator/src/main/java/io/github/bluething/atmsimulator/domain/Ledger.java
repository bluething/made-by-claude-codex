package io.github.bluething.atmsimulator.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bank's books: the balance of every account. All money rules live here, so
 * they can be read and tested in one place without any infrastructure.
 *
 * <p>A ledger is <b>not thread safe</b> and its operations are <b>not atomic</b> on
 * their own. The store that owns it runs each operation on a {@link #copy()} and
 * publishes the copy only if the operation succeeds.
 */
public final class Ledger implements LedgerView {

    private final Map<CustomerName, Money> balances;

    public Ledger() {
        this(new HashMap<>());
    }

    private Ledger(Map<CustomerName, Money> balances) {
        this.balances = balances;
    }

    /** Independent copy. Cheap enough: all values inside are immutable. */
    public Ledger copy() {
        return new Ledger(new HashMap<>(balances));
    }

    /** @return {@code true} if a new account was opened, {@code false} if it already existed */
    public boolean openAccount(CustomerName customer) {
        return balances.putIfAbsent(customer, Money.ZERO) == null;
    }

    @Override
    public boolean hasAccount(CustomerName customer) {
        return balances.containsKey(customer);
    }

    @Override
    public Money balanceOf(CustomerName customer) {
        Money balance = balances.get(customer);
        if (balance == null) {
            throw new UnknownCustomerException(customer);
        }
        return balance;
    }

    public void deposit(CustomerName customer, Money amount) {
        requirePositive(amount);
        credit(customer, amount);
    }

    /** @throws InsufficientFundsException if the balance is lower than the amount */
    public void withdraw(CustomerName customer, Money amount) {
        requirePositive(amount);
        if (balanceOf(customer).isLessThan(amount)) {
            throw new InsufficientFundsException();
        }
        debit(customer, amount);
    }

    @Override
    public AccountStatement statementOf(CustomerName customer) {
        return new AccountStatement(customer, balanceOf(customer), List.of(), List.of());
    }

    private void credit(CustomerName customer, Money amount) {
        balances.put(customer, balanceOf(customer).plus(amount));
    }

    private void debit(CustomerName customer, Money amount) {
        balances.put(customer, balanceOf(customer).minus(amount));
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidAmountException("Amount must be greater than $0");
        }
    }
}
