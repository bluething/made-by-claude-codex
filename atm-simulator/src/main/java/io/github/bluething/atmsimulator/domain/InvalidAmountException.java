package io.github.bluething.atmsimulator.domain;

import java.io.Serial;

public final class InvalidAmountException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String message) {
        super(message);
    }
}
