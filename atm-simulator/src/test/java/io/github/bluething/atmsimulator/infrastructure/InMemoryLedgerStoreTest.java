package io.github.bluething.atmsimulator.infrastructure;

import io.github.bluething.atmsimulator.domain.AccountStatement;
import io.github.bluething.atmsimulator.domain.BusinessException;
import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.DebtLine;
import io.github.bluething.atmsimulator.domain.Ledger;
import io.github.bluething.atmsimulator.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryLedgerStoreTest {

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");

    private InMemoryLedgerStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryLedgerStore();
        store.update(ledger -> ledger.openAccount(ALICE) && ledger.openAccount(BOB));
    }

    @Test
    void successfulUpdateIsVisibleToReads() {
        store.update(ledger -> ledger.deposit(ALICE, Money.of(10)));

        Money balance = store.read(view -> view.balanceOf(ALICE));
        assertThat(balance).isEqualTo(Money.of(10));
    }

    @Test
    void failedUpdateLeavesNoTrace() {
        store.update(ledger -> ledger.deposit(BOB, Money.of(5)));

        assertThatThrownBy(() -> store.update(ledger -> {
            ledger.openAccount(CustomerName.of("Carol"));
            ledger.deposit(ALICE, Money.of(100));
            ledger.transfer(BOB, ALICE, Money.of(50)); // creates a debt
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        boolean carolExists = store.read(view -> view.hasAccount(CustomerName.of("Carol")));
        AccountStatement alice = store.read(view -> view.statementOf(ALICE));
        AccountStatement bob = store.read(view -> view.statementOf(BOB));

        assertThat(carolExists).isFalse();
        assertThat(alice.balance()).isEqualTo(Money.ZERO);
        assertThat(bob.balance()).isEqualTo(Money.of(5));
        assertThat(bob.owedTo()).isEmpty();
    }

    @Test
    void ledgerLeakedFromAnUpdateCannotBeChangedOutsideTheStore() {
        Ledger leaked = store.update(ledger -> ledger);

        assertThatThrownBy(() -> leaked.deposit(ALICE, Money.of(10))).isInstanceOf(IllegalStateException.class);
        Money balance = store.read(view -> view.balanceOf(ALICE));
        assertThat(balance).isEqualTo(Money.ZERO);
    }

    @Test
    void ledgerLeakedFromAReadCannotBeChanged() {
        Ledger leaked = (Ledger) store.read(view -> view);

        assertThatThrownBy(() -> leaked.deposit(ALICE, Money.of(10))).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Many threads hammer the store with random operations. Transfers and repayments only
     * move cash between accounts, so at the end the total cash must equal what was
     * successfully deposited minus what was successfully withdrawn. Lost updates or
     * half-applied operations would break this.
     */
    @Test
    void concurrentOperationsConserveMoney() throws Exception {
        List<CustomerName> customers = IntStream.range(0, 5)
                .mapToObj(i -> CustomerName.of("c" + i))
                .toList();
        customers.forEach(customer -> store.update(ledger -> ledger.openAccount(customer)));

        int workers = 16;
        int operationsPerWorker = 500;
        List<Future<BigDecimal>> netDeposits = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            for (int w = 0; w < workers; w++) {
                netDeposits.add(executor.submit(() -> runRandomOperations(customers, operationsPerWorker)));
            }
        }

        BigDecimal expectedCash = BigDecimal.ZERO;
        for (Future<BigDecimal> net : netDeposits) {
            expectedCash = expectedCash.add(net.get()); // rethrows anything a worker failed with
        }
        BigDecimal actualCash = store.read(view -> customers.stream()
                .map(view::balanceOf)
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        assertThat(actualCash).isEqualByComparingTo(expectedCash);
        for (CustomerName customer : customers) {
            AccountStatement statement = store.read(view -> view.statementOf(customer));
            List<CustomerName> creditors = statement.owedTo().stream().map(DebtLine::counterparty).toList();
            List<CustomerName> debtors = statement.owedFrom().stream().map(DebtLine::counterparty).toList();
            assertThat(Collections.disjoint(creditors, debtors))
                    .as("nobody is both creditor and debtor of %s", customer)
                    .isTrue();
        }
    }

    /** @return successful deposits minus successful withdrawals made by this worker */
    private BigDecimal runRandomOperations(List<CustomerName> customers, int operations) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BigDecimal net = BigDecimal.ZERO;
        for (int i = 0; i < operations; i++) {
            CustomerName customer = customers.get(random.nextInt(customers.size()));
            CustomerName other = customers.get(random.nextInt(customers.size()));
            Money amount = Money.of(1 + random.nextInt(50));
            try {
                switch (random.nextInt(3)) {
                    case 0 -> {
                        store.update(ledger -> ledger.deposit(customer, amount));
                        net = net.add(amount.amount());
                    }
                    case 1 -> {
                        store.update(ledger -> {
                            ledger.withdraw(customer, amount);
                            return null;
                        });
                        net = net.subtract(amount.amount());
                    }
                    default -> store.update(ledger -> ledger.transfer(customer, other, amount));
                }
            } catch (BusinessException expected) {
                // insufficient funds or self transfer: rejected, nothing changed
            }
        }
        return net;
    }
}
