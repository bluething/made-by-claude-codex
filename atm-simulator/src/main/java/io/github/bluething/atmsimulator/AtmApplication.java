package io.github.bluething.atmsimulator;

import io.github.bluething.atmsimulator.application.AtmSession;
import io.github.bluething.atmsimulator.application.BankService;
import io.github.bluething.atmsimulator.cli.AtmConsole;
import io.github.bluething.atmsimulator.cli.CommandParser;
import io.github.bluething.atmsimulator.cli.ConsolePresenter;
import io.github.bluething.atmsimulator.infrastructure.InMemoryLedgerStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Composition root: the only place that knows every concrete class and wires them
 * together by hand. With this few objects, constructor injection without a DI
 * framework keeps start-up instant and the wiring readable in one screen.
 */
public final class AtmApplication {

    private static final Logger LOG = System.getLogger(AtmApplication.class.getName());

    private AtmApplication() {
    }

    public static void main(String[] args) {
        BankService bank = new BankService(new InMemoryLedgerStore());
        AtmConsole console = new AtmConsole(
                new AtmSession(bank),
                new CommandParser(),
                new ConsolePresenter(),
                new BufferedReader(new InputStreamReader(System.in)),
                new PrintWriter(System.out));
        try {
            console.run();
        } catch (IOException e) {
            LOG.log(Level.ERROR, "Console I/O failed", e);
            System.exit(1);
        }
    }
}
