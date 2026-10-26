package io.github.bluething.atmsimulator.cli;

import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.InvalidAmountException;
import io.github.bluething.atmsimulator.domain.InvalidCustomerNameException;
import io.github.bluething.atmsimulator.domain.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandParserTest {

    private final CommandParser parser = new CommandParser();

    static Stream<Arguments> validCommands() {
        return Stream.of(
                Arguments.of("login Alice", new Command.Login(CustomerName.of("Alice"))),
                Arguments.of("LOGIN Alice", new Command.Login(CustomerName.of("Alice"))),
                Arguments.of("  login   alice  ", new Command.Login(CustomerName.of("alice"))),
                Arguments.of("deposit 100", new Command.Deposit(Money.of(100))),
                Arguments.of("deposit 12.50", new Command.Deposit(Money.of("12.50"))),
                Arguments.of("withdraw 5", new Command.Withdraw(Money.of(5))),
                Arguments.of("transfer Bob 30", new Command.Transfer(CustomerName.of("Bob"), Money.of(30))),
                Arguments.of("logout", new Command.Logout()),
                Arguments.of("help", new Command.Help()),
                Arguments.of("Exit", new Command.Exit()));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("validCommands")
    void parsesValidCommands(String line, Command expected) {
        assertThat(parser.parse(line)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            login                 | Usage: login [name]
            login Alice Bob       | Usage: login [name]
            deposit               | Usage: deposit [amount]
            withdraw 1 2          | Usage: withdraw [amount]
            transfer Bob          | Usage: transfer [target] [amount]
            logout now            | Usage: logout
            fly                   | Unknown command 'fly'. Type 'help' to see all commands.
            """)
    void rejectsMalformedCommands(String line, String message) {
        assertThatThrownBy(() -> parser.parse(line))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessage(message);
    }

    @ParameterizedTest
    @CsvSource({"deposit abc", "deposit -5", "deposit 0", "withdraw 1.234", "transfer Bob 1e3"})
    void rejectsInvalidAmounts(String line) {
        assertThatThrownBy(() -> parser.parse(line)).isInstanceOf(InvalidAmountException.class);
    }

    @ParameterizedTest
    @CsvSource({"login Al!ce", "transfer B@b 10"})
    void rejectsInvalidNames(String line) {
        assertThatThrownBy(() -> parser.parse(line)).isInstanceOf(InvalidCustomerNameException.class);
    }
}
