package io.github.bluething.atm;

/** Money that actually moved from the current customer to {@code to}. */
public record Payment(String to, long amount) {
}
