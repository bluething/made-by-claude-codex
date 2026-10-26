package io.github.bluething.atm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmTest {

    private ByteArrayOutputStream buffer;
    private Atm atm;

    @BeforeEach
    void setUp() {
        buffer = new ByteArrayOutputStream();
        atm = new Atm(new Bank(), new PrintStream(buffer, true));
    }

    private String run(String line) {
        buffer.reset();
        atm.execute(line);
        return buffer.toString().strip();
    }

    @Test
    void loginDepositLogout() {
        assertEquals("Hello, Alice!\nYour balance is $0", run("login Alice"));
        assertEquals("Your balance is $100", run("deposit 100"));
        assertEquals("Your balance is $60", run("withdraw 40"));
        assertEquals("Goodbye, Alice!", run("logout"));
        assertEquals("Hello, Alice!\nYour balance is $60", run("login Alice"));
    }

    @Test
    void commandsRequireLogin() {
        assertEquals("Please login first", run("deposit 10"));
        assertEquals("Please login first", run("logout"));
    }

    @Test
    void cannotLoginTwice() {
        run("login Alice");
        assertEquals("Already logged in as Alice, please logout first", run("login Bob"));
    }

    @Test
    void invalidInput() {
        run("login Alice");
        assertEquals("Invalid amount: abc", run("deposit abc"));
        assertEquals("Usage: deposit [amount]", run("deposit"));
        assertEquals("Unknown command: fly", run("fly"));
        assertEquals("", run("   "));
    }
}
