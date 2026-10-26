package io.github.bluething.atmsimulator.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A non-negative amount of dollars with cent precision.
 *
 * <p>Backed by {@link BigDecimal} normalised to scale 2, so {@code 5}, {@code 5.0} and
 * {@code 5.00} are the same value and {@link #equals(Object)} behaves as expected.
 * Amounts are never rounded: a value with non-zero digits below one cent is rejected
 * instead of silently losing money ({@code 1.500} is fine, {@code 1.005} is not).
 * Customer input is held to a stricter format, see {@link #parsePositive(String)}.
 *
 * <p>A single currency is assumed, as in the problem statement.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private static final int SCALE = 2;
    /** Up to 12 integer digits (below one trillion) and up to 2 decimals. */
    private static final Pattern INPUT = Pattern.compile("\\d{1,12}(\\.\\d{1,2})?");

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + amount);
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("Money has more than " + SCALE + " decimals: " + amount);
        }
        amount = amount.setScale(SCALE);
    }

    public static Money of(long dollars) {
        return new Money(BigDecimal.valueOf(dollars));
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    /**
     * Parses customer input such as {@code 100} or {@code 12.50}.
     *
     * @throws InvalidAmountException if the input is not a valid, positive amount
     */
    public static Money parsePositive(String input) {
        if (input == null || !INPUT.matcher(input).matches()) {
            throw new InvalidAmountException(
                    "Invalid amount '" + input + "': use a positive number with at most 2 decimals");
        }
        Money money = of(input);
        if (money.isZero()) {
            throw new InvalidAmountException("Amount must be greater than $0");
        }
        return money;
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    /**
     * @throws IllegalArgumentException if the result would be negative. Callers are
     *                                  expected to check first, so this signals a bug.
     */
    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
