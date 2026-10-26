package io.github.bluething.atm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Reads commands line by line, keeps track of who is logged in
 * and prints the result. Business rules live in {@link Bank}.
 */
public class Atm {

    private final Bank bank;
    private final PrintStream out;
    private String currentUser; // null when nobody is logged in

    public Atm(Bank bank, PrintStream out) {
        this.bank = bank;
        this.out = out;
    }

    public void run(BufferedReader in) throws IOException {
        out.print("$ ");
        String line;
        while ((line = in.readLine()) != null) {
            execute(line);
            out.print("$ ");
        }
        out.println();
    }

    void execute(String line) {
        String[] args = line.trim().split("\\s+");
        String command = args[0].toLowerCase();
        if (command.isEmpty()) {
            return;
        }
        try {
            switch (command) {
                case "login" -> login(arg(args, 1, "login [name]"));
                case "deposit" -> deposit(parseAmount(arg(args, 1, "deposit [amount]")));
                case "withdraw" -> withdraw(parseAmount(arg(args, 1, "withdraw [amount]")));
                case "transfer" -> transfer(
                        arg(args, 1, "transfer [target] [amount]"),
                        parseAmount(arg(args, 2, "transfer [target] [amount]")));
                case "logout" -> logout();
                default -> out.println("Unknown command: " + args[0]);
            }
        } catch (BankException | IllegalArgumentException e) {
            out.println(e.getMessage());
        }
        out.println();
    }

    private void login(String name) {
        if (currentUser != null) {
            throw new BankException("Already logged in as " + currentUser + ", please logout first");
        }
        bank.open(name);
        currentUser = name;
        out.println("Hello, " + name + "!");
        printStatus();
    }

    private void deposit(long amount) {
        printPayments(bank.deposit(requireLogin(), amount));
        printStatus();
    }

    private void withdraw(long amount) {
        bank.withdraw(requireLogin(), amount);
        printStatus();
    }

    private void transfer(String target, long amount) {
        printPayments(bank.transfer(requireLogin(), target, amount));
        printStatus();
    }

    private void logout() {
        String name = requireLogin();
        currentUser = null;
        out.println("Goodbye, " + name + "!");
    }

    private void printPayments(List<Payment> payments) {
        for (Payment payment : payments) {
            out.println("Transferred $" + payment.amount() + " to " + payment.to());
        }
    }

    private void printStatus() {
        out.println("Your balance is $" + bank.balance(currentUser));
        for (Debt debt : bank.debtsOf(currentUser)) {
            if (debt.debtor().equals(currentUser)) {
                out.println("Owed $" + debt.amount() + " to " + debt.creditor());
            } else {
                out.println("Owed $" + debt.amount() + " from " + debt.debtor());
            }
        }
    }

    private String requireLogin() {
        if (currentUser == null) {
            throw new BankException("Please login first");
        }
        return currentUser;
    }

    private static String arg(String[] args, int index, String usage) {
        if (args.length <= index) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
        return args[index];
    }

    private static long parseAmount(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + raw);
        }
    }
}
