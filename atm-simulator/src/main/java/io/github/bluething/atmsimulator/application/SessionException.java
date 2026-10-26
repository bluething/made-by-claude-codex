package io.github.bluething.atmsimulator.application;

import io.github.bluething.atmsimulator.domain.BusinessException;
import io.github.bluething.atmsimulator.domain.CustomerName;

import java.io.Serial;

/** The command does not fit the current session state, e.g. depositing while logged out. */
public final class SessionException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    private SessionException(String message) {
        super(message);
    }

    static SessionException notLoggedIn() {
        return new SessionException("Please login first");
    }

    static SessionException alreadyLoggedIn(CustomerName customer) {
        return new SessionException("Already logged in as " + customer + ", please logout first");
    }
}
