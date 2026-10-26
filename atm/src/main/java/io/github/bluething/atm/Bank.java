package io.github.bluething.atm;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory bank. Amounts are whole dollars.
 * Not thread safe - the CLI is single user, single thread.
 */
public class Bank {

    private final Map<String, Long> balances = new HashMap<>();

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

    public void deposit(String name, long amount) {
        requirePositive(amount);
        balances.put(name, Math.addExact(balance(name), amount));
    }

    public void withdraw(String name, long amount) {
        requirePositive(amount);
        long balance = balance(name);
        if (amount > balance) {
            throw new BankException("Insufficient balance");
        }
        balances.put(name, balance - amount);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new BankException("Amount must be greater than 0");
        }
    }
}
