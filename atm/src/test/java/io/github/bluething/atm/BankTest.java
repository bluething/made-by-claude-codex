package io.github.bluething.atm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void transferWithEnoughBalance() {
        bank.open("Bob");
        bank.deposit("Bob", 80);

        List<Payment> payments = bank.transfer("Bob", "Alice", 50);

        assertEquals(List.of(new Payment("Alice", 50)), payments);
        assertEquals(30, bank.balance("Bob"));
        assertEquals(50, bank.balance("Alice"));
        assertEquals(List.of(), bank.debtsOf("Bob"));
    }

    @Test
    void transferMoreThanBalanceCreatesDebt() {
        bank.open("Bob");
        bank.deposit("Bob", 30);

        List<Payment> payments = bank.transfer("Bob", "Alice", 100);

        assertEquals(List.of(new Payment("Alice", 30)), payments);
        assertEquals(0, bank.balance("Bob"));
        assertEquals(List.of(new Debt("Bob", "Alice", 70)), bank.debtsOf("Bob"));
        assertEquals(List.of(new Debt("Bob", "Alice", 70)), bank.debtsOf("Alice"));
    }

    @Test
    void debtAccumulates() {
        bank.open("Bob");
        bank.transfer("Bob", "Alice", 10);
        bank.transfer("Bob", "Alice", 5);
        assertEquals(List.of(new Debt("Bob", "Alice", 15)), bank.debtsOf("Bob"));
    }

    @Test
    void cannotTransferToSelfOrUnknown() {
        bank.deposit("Alice", 10);
        assertThrows(BankException.class, () -> bank.transfer("Alice", "Alice", 5));
        assertThrows(BankException.class, () -> bank.transfer("Alice", "Nobody", 5));
        assertEquals(10, bank.balance("Alice"));
    }

    @Test
    void depositRepaysDebt() {
        bank.open("Bob");
        bank.transfer("Bob", "Alice", 40);

        List<Payment> payments = bank.deposit("Bob", 30);

        assertEquals(List.of(new Payment("Alice", 30)), payments);
        assertEquals(0, bank.balance("Bob"));
        assertEquals(30, bank.balance("Alice"));
        assertEquals(List.of(new Debt("Bob", "Alice", 10)), bank.debtsOf("Bob"));
    }

    @Test
    void depositRepaysOldestDebtFirstAndKeepsTheRest() {
        bank.open("Bob");
        bank.open("Carol");
        bank.transfer("Bob", "Carol", 20);
        bank.transfer("Bob", "Alice", 20);

        List<Payment> payments = bank.deposit("Bob", 50);

        assertEquals(List.of(new Payment("Carol", 20), new Payment("Alice", 20)), payments);
        assertEquals(10, bank.balance("Bob"));
        assertEquals(List.of(), bank.debtsOf("Bob"));
    }
}
