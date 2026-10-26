package io.github.bluething.atmsimulator.domain;

import java.io.Serial;

public final class UnknownCustomerException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnknownCustomerException(CustomerName customer) {
        super("Customer " + customer + " does not exist");
    }
}
