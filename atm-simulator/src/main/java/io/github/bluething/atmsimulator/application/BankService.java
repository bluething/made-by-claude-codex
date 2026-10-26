package io.github.bluething.atmsimulator.application;

import io.github.bluething.atmsimulator.domain.AccountStatement;
import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.Money;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;

/**
 * Use cases of the bank. Each method is one transaction on the {@link LedgerStore};
 * the business rules themselves live in the domain {@code Ledger}.
 *
 * <p>Thread safe and stateless, so one instance can serve any number of ATMs.
 *
 * <p>Logs at DEBUG only: the console shares the terminal with stderr, and an audit
 * trail would belong in the store, not in logs.
 */
public final class BankService {

    private static final Logger LOG = System.getLogger(BankService.class.getName());

    private final LedgerStore store;

    public BankService(LedgerStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Opens an account for a new customer; an existing customer keeps theirs. */
    public AccountStatement openOrGetAccount(CustomerName customer) {
        record Opened(boolean isNew, AccountStatement statement) {
        }
        Opened opened = store.update(ledger -> {
            boolean isNew = ledger.openAccount(customer);
            return new Opened(isNew, ledger.statementOf(customer));
        });
        if (opened.isNew()) {
            LOG.log(Level.DEBUG, "Opened account for {0}", customer);
        }
        return opened.statement();
    }

    public OperationResult deposit(CustomerName customer, Money amount) {
        OperationResult result = store.update(ledger ->
                new OperationResult(ledger.deposit(customer, amount), ledger.statementOf(customer)));
        LOG.log(Level.DEBUG, "{0} deposited {1}, repaid {2}", customer, amount, result.payments());
        return result;
    }

    public OperationResult withdraw(CustomerName customer, Money amount) {
        OperationResult result = store.update(ledger -> {
            ledger.withdraw(customer, amount);
            return new OperationResult(List.of(), ledger.statementOf(customer));
        });
        LOG.log(Level.DEBUG, "{0} withdrew {1}", customer, amount);
        return result;
    }

    public OperationResult transfer(CustomerName from, CustomerName to, Money amount) {
        OperationResult result = store.update(ledger ->
                new OperationResult(ledger.transfer(from, to, amount), ledger.statementOf(from)));
        LOG.log(Level.DEBUG, "{0} transferred {1} to {2}, paid {3}", from, amount, to, result.payments());
        return result;
    }

    public AccountStatement statementOf(CustomerName customer) {
        return store.read(ledger -> ledger.statementOf(customer));
    }
}
