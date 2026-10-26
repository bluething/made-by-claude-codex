package io.github.bluething.atmsimulator.domain;

import java.io.Serial;

public final class InsufficientFundsException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException() {
        super("Insufficient balance");
    }
}
