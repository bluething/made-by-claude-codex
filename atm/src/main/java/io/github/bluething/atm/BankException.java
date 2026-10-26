package io.github.bluething.atm;

/** Business rule violation. The message is shown to the user as-is. */
public class BankException extends RuntimeException {
    public BankException(String message) {
        super(message);
    }
}
