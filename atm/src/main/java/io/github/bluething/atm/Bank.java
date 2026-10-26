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
        requirePositive(amount);
        balances.put(name, Math.addExact(balance(name), amount));
        return repayDebts(name);
    }

    public void withdraw(String name, long amount) {
        requirePositive(amount);
        long balance = balance(name);
        if (amount > balance) {
            throw new BankException("Insufficient balance");
        }
        balances.put(name, balance - amount);
    }

    /**
     * Sends as much as {@code from} can afford. Whatever is left
     * becomes a debt from {@code from} to {@code to}.
     */
    public List<Payment> transfer(String from, String to, long amount) {
        requirePositive(amount);
        if (from.equals(to)) {
            throw new BankException("Cannot transfer to yourself");
        }
        if (!exists(to)) {
            throw new BankException("Unknown customer: " + to);
        }

        List<Payment> payments = new ArrayList<>();
        long cash = Math.min(balance(from), amount);
        if (cash > 0) {
            move(from, to, cash);
            payments.add(new Payment(to, cash));
        }
        long shortfall = amount - cash;
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

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new BankException("Amount must be greater than 0");
        }
    }
}
