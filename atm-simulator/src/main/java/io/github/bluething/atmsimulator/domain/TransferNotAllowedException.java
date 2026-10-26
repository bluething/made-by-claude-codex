package io.github.bluething.atmsimulator.domain;

import java.io.Serial;

public final class TransferNotAllowedException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TransferNotAllowedException(String message) {
        super(message);
    }
}
