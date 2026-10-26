package io.github.bluething.atmsimulator;

import io.github.bluething.atmsimulator.application.AtmSession;
import io.github.bluething.atmsimulator.application.BankService;
import io.github.bluething.atmsimulator.cli.AtmConsole;
import io.github.bluething.atmsimulator.cli.CommandParser;
import io.github.bluething.atmsimulator.cli.ConsolePresenter;
import io.github.bluething.atmsimulator.infrastructure.InMemoryLedgerStore;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test: the example session from the problem statement, through every layer.
 * The same fixture files are used by {@code scripts/smoke-test.sh} against the real jar.
 */
class ExampleSessionTest {

    @Test
    void reproducesTheExampleFromTheProblemStatement() throws IOException {
        StringWriter output = new StringWriter();
        AtmConsole console = new AtmConsole(
                new AtmSession(new BankService(new InMemoryLedgerStore())),
                new CommandParser(),
                new ConsolePresenter(),
                new BufferedReader(new StringReader(fixture("input.txt"))),
                new PrintWriter(output));

        console.run();

        assertThat(output.toString()).isEqualTo(fixture("expected.txt"));
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = ExampleSessionTest.class.getResourceAsStream("/example-session/" + name)) {
            assertThat(in).as("fixture %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
