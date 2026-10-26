package io.github.bluething.atmsimulator.domain;

import java.io.Serial;

public final class InvalidCustomerNameException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCustomerNameException(String message) {
        super(message);
    }
}
