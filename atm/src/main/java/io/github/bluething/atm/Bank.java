package io.github.bluething.atm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory bank. Amounts are whole dollars.
 * Not thread safe - the CLI is single user, single thread.
 */
public class Bank {

    /**
     * Per operation limit, like a real ATM. It also keeps balances and debts far
     * away from Long.MAX_VALUE, so addExact can't fail halfway through a repayment
     * and leave balances half updated.
     */
    static final long MAX_AMOUNT = 1_000_000_000L;

    private final Map<String, Long> balances = new HashMap<>();
    // Insertion ordered so the oldest debt comes first.
    private final Map<DebtKey, Long> debts = new LinkedHashMap<>();

    private record DebtKey(String debtor, String creditor) {
    }

    /** Creates the account if it does not exist yet. */
    public void open(String name) {
        balances.putIfAbsent(name, 0L);
    }

    public boolean exists(String name) {
        return balances.containsKey(name);
    }

    public long balance(String name) {
        Long balance = balances.get(name);
        if (balance == null) {
            throw new BankException("Unknown customer: " + name);
        }
        return balance;
    }

    /** Deposits, then uses the new balance to pay back debts, oldest first. */
    public List<Payment> deposit(String name, long amount) {
        validateAmount(amount);
        balances.put(name, Math.addExact(balance(name), amount));
        return repayDebts(name);
    }

    public void withdraw(String name, long amount) {
        validateAmount(amount);
        long balance = balance(name);
        if (amount > balance) {
            throw new BankException("Insufficient balance");
        }
        balances.put(name, balance - amount);
    }

    /**
     * If {@code to} owes {@code from}, that debt is reduced first and no money moves.
     * The rest is paid from the balance, whatever cannot be paid
     * becomes a debt from {@code from} to {@code to}.
     */
    public List<Payment> transfer(String from, String to, long amount) {
        validateAmount(amount);
        if (from.equals(to)) {
            throw new BankException("Cannot transfer to yourself");
        }
        if (!exists(to)) {
            throw new BankException("Unknown customer: " + to);
        }

        long remaining = amount - reduceDebt(to, from, amount);

        List<Payment> payments = new ArrayList<>();
        long cash = Math.min(balance(from), remaining);
        if (cash > 0) {
            move(from, to, cash);
            payments.add(new Payment(to, cash));
        }
        long shortfall = remaining - cash;
        if (shortfall > 0) {
            debts.merge(new DebtKey(from, to), shortfall, Math::addExact);
        }
        return payments;
    }

    /** Debts where the customer is either the debtor or the creditor. */
    public List<Debt> debtsOf(String name) {
        List<Debt> result = new ArrayList<>();
        debts.forEach((key, amount) -> {
            if (key.debtor().equals(name) || key.creditor().equals(name)) {
                result.add(new Debt(key.debtor(), key.creditor(), amount));
            }
        });
        return result;
    }

    /** Reduces what debtor owes creditor by at most {@code max}, returns the reduced amount. */
    private long reduceDebt(String debtor, String creditor, long max) {
        DebtKey key = new DebtKey(debtor, creditor);
        Long owed = debts.get(key);
        if (owed == null) {
            return 0;
        }
        long reduced = Math.min(owed, max);
        if (reduced == owed) {
            debts.remove(key);
        } else {
            debts.put(key, owed - reduced);
        }
        return reduced;
    }

    private List<Payment> repayDebts(String debtor) {
        List<Payment> payments = new ArrayList<>();
        Iterator<Map.Entry<DebtKey, Long>> it = debts.entrySet().iterator();
        while (it.hasNext() && balance(debtor) > 0) {
            Map.Entry<DebtKey, Long> debt = it.next();
            if (!debt.getKey().debtor().equals(debtor)) {
                continue;
            }
            String creditor = debt.getKey().creditor();
            long paid = Math.min(balance(debtor), debt.getValue());
            move(debtor, creditor, paid);
            payments.add(new Payment(creditor, paid));
            if (paid == debt.getValue()) {
                it.remove();
            } else {
                debt.setValue(debt.getValue() - paid);
            }
        }
        return payments;
    }

    private void move(String from, String to, long amount) {
        balances.put(from, balance(from) - amount);
        balances.put(to, Math.addExact(balance(to), amount));
    }

    private static void validateAmount(long amount) {
        if (amount <= 0) {
            throw new BankException("Amount must be greater than 0");
        }
        if (amount > MAX_AMOUNT) {
            throw new BankException("Amount must not exceed $" + MAX_AMOUNT);
        }
    }
}
