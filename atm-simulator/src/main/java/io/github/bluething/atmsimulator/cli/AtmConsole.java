package io.github.bluething.atmsimulator.cli;

import io.github.bluething.atmsimulator.application.AtmSession;
import io.github.bluething.atmsimulator.domain.BusinessException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;

/**
 * The read-eval-print loop: reads a line, runs it against the {@link AtmSession} and
 * prints the outcome.
 *
 * <p>Error policy:
 * <ul>
 *   <li>invalid input or a rejected operation: show the reason and keep going;</li>
 *   <li>unexpected runtime failure: log it, show a generic message and keep going. This is
 *       safe because every operation is atomic, so the bank is still consistent;</li>
 *   <li>I/O failure on the console itself: stop, there is nobody left to talk to.</li>
 * </ul>
 */
public final class AtmConsole {

    static final String PROMPT = "$ ";
    static final String UNEXPECTED_ERROR = "Sorry, something went wrong. Please try again.";

    private static final Logger LOG = System.getLogger(AtmConsole.class.getName());

    private final AtmSession session;
    private final CommandParser parser;
    private final ConsolePresenter presenter;
    private final BufferedReader in;
    private final PrintWriter out;
    private boolean exitRequested;

    public AtmConsole(AtmSession session, CommandParser parser, ConsolePresenter presenter,
                      BufferedReader in, PrintWriter out) {
        this.session = Objects.requireNonNull(session, "session");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
    }

    /**
     * Runs until the user types {@code exit} or the input ends.
     *
     * @throws IOException if the input can't be read or the output can't be written
     */
    public void run() throws IOException {
        while (!exitRequested) {
            out.print(PROMPT);
            flush();
            String line = in.readLine();
            if (line == null) {
                out.println();
                break;
            }
            if (!line.isBlank()) {
                print(handle(line));
            }
        }
        flush();
    }

    /** PrintWriter swallows I/O errors, so they have to be checked explicitly. */
    private void flush() throws IOException {
        if (out.checkError()) { // checkError() also flushes
            throw new IOException("Console output failed");
        }
    }

    private List<String> handle(String line) {
        try {
            return execute(parser.parse(line));
        } catch (InvalidCommandException | BusinessException e) {
            return List.of(e.getMessage());
        } catch (RuntimeException e) {
            // The raw line is not logged: it may contain customer data.
            LOG.log(Level.ERROR, "Unexpected failure while handling a command", e);
            return List.of(UNEXPECTED_ERROR);
        }
    }

    private List<String> execute(Command command) {
        return switch (command) {
            case Command.Login(var customer) -> presenter.loggedIn(session.login(customer));
            case Command.Deposit(var amount) -> presenter.operation(session.deposit(amount));
            case Command.Withdraw(var amount) -> presenter.operation(session.withdraw(amount));
            case Command.Transfer(var target, var amount) -> presenter.operation(session.transfer(target, amount));
            case Command.Logout() -> presenter.loggedOut(session.logout());
            case Command.Help() -> presenter.help();
            case Command.Exit() -> {
                exitRequested = true;
                yield session.currentCustomer().isPresent()
                        ? presenter.loggedOut(session.logout())
                        : List.of();
            }
        };
    }

    private void print(List<String> lines) {
        lines.forEach(out::println);
        out.println();
    }
}
