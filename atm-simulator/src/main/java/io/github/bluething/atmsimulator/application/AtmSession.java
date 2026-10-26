package io.github.bluething.atmsimulator.application;

import io.github.bluething.atmsimulator.domain.AccountStatement;
import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * The state of one ATM: who is logged in. Money operations act on that customer.
 *
 * <p>The bank is shared by every ATM; a session belongs to exactly one, so it is
 * <b>not thread safe</b> on purpose.
 */
public final class AtmSession {

    private final BankService bank;
    private CustomerName customer; // null while nobody is logged in

    public AtmSession(BankService bank) {
        this.bank = Objects.requireNonNull(bank, "bank");
    }

    /** Logs in, opening an account first if this is a new customer. */
    public AccountStatement login(CustomerName name) {
        if (customer != null) {
            throw SessionException.alreadyLoggedIn(customer);
        }
        AccountStatement statement = bank.openOrGetAccount(name);
        customer = name; // only once the bank call succeeded
        return statement;
    }

    /** @return the customer who was logged in */
    public CustomerName logout() {
        CustomerName loggedOut = requireLogin();
        customer = null;
        return loggedOut;
    }

    public OperationResult deposit(Money amount) {
        return bank.deposit(requireLogin(), amount);
    }

    public OperationResult withdraw(Money amount) {
        return bank.withdraw(requireLogin(), amount);
    }

    public OperationResult transfer(CustomerName target, Money amount) {
        return bank.transfer(requireLogin(), target, amount);
    }

    public Optional<CustomerName> currentCustomer() {
        return Optional.ofNullable(customer);
    }

    private CustomerName requireLogin() {
        if (customer == null) {
            throw SessionException.notLoggedIn();
        }
        return customer;
    }
}
