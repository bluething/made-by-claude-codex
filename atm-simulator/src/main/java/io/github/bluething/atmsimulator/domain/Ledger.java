package io.github.bluething.atmsimulator.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The bank's books: the balance of every account and every debt between customers.
 * All money rules live here, so they can be read and tested in one place without
 * any infrastructure.
 *
 * <p>Invariants:
 * <ul>
 *   <li>balances are never negative</li>
 *   <li>debts are always positive, and there is at most one debt per (debtor, creditor) pair</li>
 *   <li>two customers never owe each other at the same time</li>
 * </ul>
 *
 * <p>A ledger is <b>not thread safe</b> and its operations are <b>not atomic</b> on
 * their own. The store that owns it runs each operation on a {@link #copy()} and
 * publishes the copy only if the operation succeeds.
 */
public final class Ledger implements LedgerView {

    private record DebtKey(CustomerName debtor, CustomerName creditor) {
    }

    private static final Comparator<Debt> OLDEST_FIRST = Comparator.comparingLong(Debt::sequence);

    private final Map<CustomerName, Money> balances;
    private final Map<DebtKey, Debt> debts;
    private long nextDebtSequence;

    public Ledger() {
        this(new HashMap<>(), new HashMap<>(), 0);
    }

    private Ledger(Map<CustomerName, Money> balances, Map<DebtKey, Debt> debts, long nextDebtSequence) {
        this.balances = balances;
        this.debts = debts;
        this.nextDebtSequence = nextDebtSequence;
    }

    /** Independent copy. Cheap enough: all values inside are immutable. */
    public Ledger copy() {
        return new Ledger(new HashMap<>(balances), new HashMap<>(debts), nextDebtSequence);
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

    /**
     * Deposits {@code amount}, then uses the whole balance to pay back the customer's
     * debts, oldest first, until the balance or the debts run out. Creditors receiving
     * a repayment do not repay their own debts in turn.
     *
     * @return the repayments made, in order
     */
    public List<Payment> deposit(CustomerName customer, Money amount) {
        requirePositive(amount);
        credit(customer, amount);
        return repayDebtsOf(customer);
    }

    /** @throws InsufficientFundsException if the balance is lower than the amount */
    public void withdraw(CustomerName customer, Money amount) {
        requirePositive(amount);
        if (balanceOf(customer).isLessThan(amount)) {
            throw new InsufficientFundsException();
        }
        debit(customer, amount);
    }

    /**
     * Transfers {@code amount} from {@code from} to {@code to} in three steps:
     * <ol>
     *   <li>If {@code to} owes {@code from}, that debt is cancelled first. No money moves for this part.</li>
     *   <li>What is left is paid from {@code from}'s balance, as far as it goes.</li>
     *   <li>Whatever could not be paid becomes a debt from {@code from} to {@code to}.</li>
     * </ol>
     * Receiving money does not make {@code to} pay back their own debts; only a deposit does.
     *
     * @return the money that actually moved: empty, or a single payment
     */
    public List<Payment> transfer(CustomerName from, CustomerName to, Money amount) {
        requirePositive(amount);
        if (from.equals(to)) {
            throw new TransferNotAllowedException("Cannot transfer to yourself");
        }
        Money available = balanceOf(from);
        if (!hasAccount(to)) {
            throw new UnknownCustomerException(to);
        }

        Money remaining = amount.minus(cancelDebt(to, from, amount));
        Money paid = remaining.min(available);
        Money shortfall = remaining.minus(paid);

        if (shortfall.isPositive()) {
            addDebt(from, to, shortfall);
        }
        if (paid.isZero()) {
            return List.of();
        }
        move(from, to, paid);
        return List.of(new Payment(from, to, paid));
    }

    @Override
    public AccountStatement statementOf(CustomerName customer) {
        return new AccountStatement(
                customer,
                balanceOf(customer),
                debtsWhere(debt -> debt.debtor().equals(customer))
                        .stream().map(debt -> new DebtLine(debt.creditor(), debt.amount())).toList(),
                debtsWhere(debt -> debt.creditor().equals(customer))
                        .stream().map(debt -> new DebtLine(debt.debtor(), debt.amount())).toList());
    }

    private List<Payment> repayDebtsOf(CustomerName debtor) {
        List<Payment> repayments = new ArrayList<>();
        for (Debt debt : debtsWhere(d -> d.debtor().equals(debtor))) {
            Money balance = balanceOf(debtor);
            if (balance.isZero()) {
                break;
            }
            Money paid = debt.amount().min(balance);
            move(debtor, debt.creditor(), paid);
            settle(new DebtKey(debtor, debt.creditor()), debt, paid);
            repayments.add(new Payment(debtor, debt.creditor(), paid));
        }
        return List.copyOf(repayments);
    }

    /** Reduces what {@code debtor} owes {@code creditor} by at most {@code max}. */
    private Money cancelDebt(CustomerName debtor, CustomerName creditor, Money max) {
        DebtKey key = new DebtKey(debtor, creditor);
        Debt debt = debts.get(key);
        if (debt == null) {
            return Money.ZERO;
        }
        Money cancelled = debt.amount().min(max);
        settle(key, debt, cancelled);
        return cancelled;
    }

    private void addDebt(CustomerName debtor, CustomerName creditor, Money amount) {
        DebtKey key = new DebtKey(debtor, creditor);
        Debt existing = debts.get(key);
        debts.put(key, existing == null
                ? new Debt(debtor, creditor, amount, nextDebtSequence++)
                : existing.increasedBy(amount));
    }

    private void settle(DebtKey key, Debt debt, Money amount) {
        debt.reducedBy(amount).ifPresentOrElse(
                remaining -> debts.put(key, remaining),
                () -> debts.remove(key));
    }

    private List<Debt> debtsWhere(Predicate<Debt> filter) {
        return debts.values().stream().filter(filter).sorted(OLDEST_FIRST).toList();
    }

    private void move(CustomerName from, CustomerName to, Money amount) {
        debit(from, amount);
        credit(to, amount);
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
