package io.github.bluething.atm;

/** {@code debtor} owes {@code creditor} the given amount. */
public record Debt(String debtor, String creditor, long amount) {
}
