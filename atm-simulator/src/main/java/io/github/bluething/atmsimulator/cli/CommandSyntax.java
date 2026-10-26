package io.github.bluething.atmsimulator.cli;

import java.util.Locale;
import java.util.Optional;

/** The text form of each command. Single source for parsing, usage errors and help. */
enum CommandSyntax {

    LOGIN("login", "[name]", "Logs in as this customer and creates the customer if not exist"),
    DEPOSIT("deposit", "[amount]", "Deposits this amount to the logged in customer"),
    WITHDRAW("withdraw", "[amount]", "Withdraws this amount from the logged in customer"),
    TRANSFER("transfer", "[target] [amount]",
            "Transfers this amount from the logged in customer to the target customer"),
    LOGOUT("logout", "", "Logs out of the current customer"),
    HELP("help", "", "Shows this help"),
    EXIT("exit", "", "Ends the session");

    private final String keyword;
    private final String arguments;
    private final String description;

    CommandSyntax(String keyword, String arguments, String description) {
        this.keyword = keyword;
        this.arguments = arguments;
        this.description = description;
    }

    /** Keywords are case insensitive; arguments such as names keep their case. */
    static Optional<CommandSyntax> forKeyword(String keyword) {
        String normalised = keyword.toLowerCase(Locale.ROOT);
        for (CommandSyntax syntax : values()) {
            if (syntax.keyword.equals(normalised)) {
                return Optional.of(syntax);
            }
        }
        return Optional.empty();
    }

    int argumentCount() {
        return arguments.isEmpty() ? 0 : arguments.split(" ").length;
    }

    String usage() {
        return arguments.isEmpty() ? keyword : keyword + " " + arguments;
    }

    String description() {
        return description;
    }
}
