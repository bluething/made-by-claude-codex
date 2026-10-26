package io.github.bluething.atmsimulator.cli;

import java.io.Serial;

/** The input line is not a well-formed command. The message tells the user how to fix it. */
public final class InvalidCommandException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidCommandException(String message) {
        super(message);
    }
}
