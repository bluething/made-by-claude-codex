package io.github.bluething.atmsimulator.cli;

import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.Money;

import java.util.regex.Pattern;

/**
 * Turns one line of input into a {@link Command}. Arguments are validated here, at the
 * edge, by building value objects; the layers below never see raw strings.
 */
public final class CommandParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * @param line a non-blank input line
     * @throws InvalidCommandException if the command is unknown or has the wrong number of arguments
     * @throws io.github.bluething.atmsimulator.domain.BusinessException if a name or amount is invalid
     */
    public Command parse(String line) {
        String[] tokens = WHITESPACE.split(line.strip());
        CommandSyntax syntax = CommandSyntax.forKeyword(tokens[0]).orElseThrow(() ->
                new InvalidCommandException("Unknown command '" + tokens[0] + "'. Type 'help' to see all commands."));
        if (tokens.length - 1 != syntax.argumentCount()) {
            throw new InvalidCommandException("Usage: " + syntax.usage());
        }

        return switch (syntax) {
            case LOGIN -> new Command.Login(CustomerName.of(tokens[1]));
            case DEPOSIT -> new Command.Deposit(Money.parsePositive(tokens[1]));
            case WITHDRAW -> new Command.Withdraw(Money.parsePositive(tokens[1]));
            case TRANSFER -> new Command.Transfer(CustomerName.of(tokens[1]), Money.parsePositive(tokens[2]));
            case LOGOUT -> new Command.Logout();
            case HELP -> new Command.Help();
            case EXIT -> new Command.Exit();
        };
    }
}
