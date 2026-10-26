package io.github.bluething.atmsimulator.cli;

import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.Money;

/**
 * A parsed, validated command. Sealed, so a {@code switch} over it is checked by the
 * compiler: adding a command without handling it is a compile error, not a runtime bug.
 */
public sealed interface Command {

    record Login(CustomerName customer) implements Command {
    }

    record Deposit(Money amount) implements Command {
    }

    record Withdraw(Money amount) implements Command {
    }

    record Transfer(CustomerName target, Money amount) implements Command {
    }

    record Logout() implements Command {
    }

    record Help() implements Command {
    }

    record Exit() implements Command {
    }
}
