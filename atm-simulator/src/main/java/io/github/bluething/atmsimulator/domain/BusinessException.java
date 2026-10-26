package io.github.bluething.atmsimulator.domain;

import java.io.Serial;

/**
 * Base type for an expected failure caused by a business rule, such as an
 * insufficient balance. The message is safe to show to the customer.
 *
 * <p>Unexpected failures (bugs, I/O errors) must not use this type, so the
 * console can tell "your request was rejected" apart from "something broke".
 */
public abstract class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected BusinessException(String message) {
        super(message);
    }
}
