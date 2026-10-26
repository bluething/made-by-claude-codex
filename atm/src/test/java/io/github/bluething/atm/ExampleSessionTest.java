package io.github.bluething.atm;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The example session from the problem statement, end to end. */
class ExampleSessionTest {

    @Test
    void exampleFromProblemStatement() throws IOException {
        String input = """
                login Alice
                deposit 100
                logout
                login Bob
                deposit 80
                transfer Alice 50
                transfer Alice 100
                deposit 30
                logout
                login Alice
                transfer Bob 30
                logout
                login Bob
                deposit 100
                logout
                """;

        String expected = """
                $ Hello, Alice!
                Your balance is $0

                $ Your balance is $100

                $ Goodbye, Alice!

                $ Hello, Bob!
                Your balance is $0

                $ Your balance is $80

                $ Transferred $50 to Alice
                Your balance is $30

                $ Transferred $30 to Alice
                Your balance is $0
                Owed $70 to Alice

                $ Transferred $30 to Alice
                Your balance is $0
                Owed $40 to Alice

                $ Goodbye, Bob!

                $ Hello, Alice!
                Your balance is $210
                Owed $40 from Bob

                $ Your balance is $210
                Owed $10 from Bob

                $ Goodbye, Alice!

                $ Hello, Bob!
                Your balance is $0
                Owed $10 to Alice

                $ Transferred $10 to Alice
                Your balance is $90

                $ Goodbye, Bob!

                $
                """;

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new Atm(new Bank(), new PrintStream(buffer, true)).run(new BufferedReader(new StringReader(input)));

        assertEquals(expected.strip(), buffer.toString().strip());
    }
}
