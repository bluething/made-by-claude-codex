package io.github.bluething.atm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BankTest {

    private Bank bank;

    @BeforeEach
    void setUp() {
        bank = new Bank();
        bank.open("Alice");
    }

    @Test
    void newAccountStartsWithZero() {
        assertEquals(0, bank.balance("Alice"));
    }

    @Test
    void openIsIdempotent() {
        bank.deposit("Alice", 10);
        bank.open("Alice");
        assertEquals(10, bank.balance("Alice"));
    }

    @Test
    void depositAndWithdraw() {
        bank.deposit("Alice", 100);
        bank.withdraw("Alice", 30);
        assertEquals(70, bank.balance("Alice"));
    }

    @Test
    void cannotWithdrawMoreThanBalance() {
        bank.deposit("Alice", 10);
        assertThrows(BankException.class, () -> bank.withdraw("Alice", 11));
        assertEquals(10, bank.balance("Alice"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThrows(BankException.class, () -> bank.deposit("Alice", 0));
        assertThrows(BankException.class, () -> bank.withdraw("Alice", -5));
    }
}
