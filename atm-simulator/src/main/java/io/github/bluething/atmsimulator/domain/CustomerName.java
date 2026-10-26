package io.github.bluething.atmsimulator.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies a customer. Validated once at the edge so the rest of the code can
 * pass names around without re-checking them.
 *
 * <p>Names are case sensitive: {@code alice} and {@code Alice} are different customers.
 */
public record CustomerName(String value) implements Comparable<CustomerName> {

    public static final int MAX_LENGTH = 32;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]+");

    public CustomerName {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new InvalidCustomerNameException(
                    "Customer name must be between 1 and " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new InvalidCustomerNameException(
                    "Customer name may only contain letters, digits, '_' and '-'");
        }
    }

    public static CustomerName of(String value) {
        return new CustomerName(value);
    }

    @Override
    public int compareTo(CustomerName other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
