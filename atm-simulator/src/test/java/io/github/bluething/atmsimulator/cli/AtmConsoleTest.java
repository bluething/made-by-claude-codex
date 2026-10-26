package io.github.bluething.atmsimulator.cli;

import io.github.bluething.atmsimulator.application.AtmSession;
import io.github.bluething.atmsimulator.application.BankService;
import io.github.bluething.atmsimulator.application.FailingLedgerStore;
import io.github.bluething.atmsimulator.application.LedgerStore;
import io.github.bluething.atmsimulator.infrastructure.InMemoryLedgerStore;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtmConsoleTest {

    private static String run(String input) throws IOException {
        return run(new InMemoryLedgerStore(), new StringReader(input));
    }

    private static String run(LedgerStore store, Reader input) throws IOException {
        StringWriter output = new StringWriter();
        run(store, input, new PrintWriter(output));
        return output.toString();
    }

    private static void run(LedgerStore store, Reader input, PrintWriter output) throws IOException {
        new AtmConsole(
                new AtmSession(new BankService(store)),
                new CommandParser(),
                new ConsolePresenter(),
                new BufferedReader(input),
                output).run();
    }

    @Test
    void printsPromptAndStopsAtEndOfInput() throws IOException {
        assertThat(run("")).isEqualTo("$ \n");
    }

    @Test
    void ignoresBlankLines() throws IOException {
        assertThat(run("\n   \n")).isEqualTo("$ $ $ \n");
    }

    @Test
    void showsErrorsAndKeepsGoing() throws IOException {
        String output = run("""
                deposit 10
                fly
                login Alice
                deposit abc
                withdraw 5
                deposit 1
                """);

        assertThat(output).isEqualTo("""
                $ Please login first

                $ Unknown command 'fly'. Type 'help' to see all commands.

                $ Hello, Alice!
                Your balance is $0

                $ Invalid amount 'abc': use a positive number with at most 2 decimals

                $ Insufficient balance

                $ Your balance is $1

                $\s
                """);
    }

    @Test
    void exitLogsOutAndStopsReadingInput() throws IOException {
        String output = run("""
                login Alice
                exit
                deposit 10
                """);

        assertThat(output).endsWith("$ Goodbye, Alice!\n\n").doesNotContain("$10");
    }

    @Test
    void exitWhenLoggedOutJustStops() throws IOException {
        assertThat(run("exit\n")).isEqualTo("$ \n");
    }

    @Test
    void unexpectedFailureShowsGenericMessageAndKeepsGoing() throws IOException {
        String output = run(new FailingLedgerStore(), new StringReader("login Alice\nhelp\n"));

        assertThat(output)
                .contains(AtmConsole.UNEXPECTED_ERROR)
                .doesNotContain("store unavailable")
                .contains("Commands:");
    }

    @Test
    void inputFailureStopsTheConsole() {
        Reader broken = new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("stdin closed");
            }

            @Override
            public void close() {
            }
        };

        assertThatThrownBy(() -> run(new InMemoryLedgerStore(), broken)).isInstanceOf(IOException.class);
    }

    @Test
    void outputFailureStopsTheConsole() {
        Writer broken = new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("stdout closed");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        assertThatThrownBy(() -> run(new InMemoryLedgerStore(), new StringReader("login Alice\n"), new PrintWriter(broken)))
                .isInstanceOf(IOException.class)
                .hasMessage("Console output failed");
    }
}
