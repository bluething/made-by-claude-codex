package io.github.bluething.atmsimulator.application;

import io.github.bluething.atmsimulator.domain.CustomerName;
import io.github.bluething.atmsimulator.domain.Money;
import io.github.bluething.atmsimulator.domain.UnknownCustomerException;
import io.github.bluething.atmsimulator.infrastructure.InMemoryLedgerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtmSessionTest {

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");

    private BankService bank;
    private AtmSession session;

    @BeforeEach
    void setUp() {
        bank = new BankService(new InMemoryLedgerStore());
        session = new AtmSession(bank);
    }

    @Test
    void loginOpensAccountAndRemembersCustomer() {
        assertThat(session.login(ALICE).balance()).isEqualTo(Money.ZERO);
        assertThat(session.currentCustomer()).contains(ALICE);
    }

    @Test
    void operationsActOnTheLoggedInCustomer() {
        bank.openOrGetAccount(BOB);
        session.login(ALICE);

        session.deposit(Money.of(100));
        session.withdraw(Money.of(10));
        session.transfer(BOB, Money.of(40));

        assertThat(bank.statementOf(ALICE).balance()).isEqualTo(Money.of(50));
        assertThat(bank.statementOf(BOB).balance()).isEqualTo(Money.of(40));
    }

    @Test
    void logoutReturnsWhoLoggedOutAndEndsTheSession() {
        session.login(ALICE);

        assertThat(session.logout()).isEqualTo(ALICE);
        assertThat(session.currentCustomer()).isEmpty();
    }

    @Test
    void cannotLoginTwice() {
        session.login(ALICE);

        assertThatThrownBy(() -> session.login(BOB))
                .isInstanceOf(SessionException.class)
                .hasMessage("Already logged in as Alice, please logout first");
        assertThat(session.currentCustomer()).contains(ALICE);
    }

    @Test
    void everythingElseRequiresLogin() {
        assertThatThrownBy(() -> session.deposit(Money.of(1))).isInstanceOf(SessionException.class)
                .hasMessage("Please login first");
        assertThatThrownBy(() -> session.withdraw(Money.of(1))).isInstanceOf(SessionException.class);
        assertThatThrownBy(() -> session.transfer(BOB, Money.of(1))).isInstanceOf(SessionException.class);
        assertThatThrownBy(() -> session.logout()).isInstanceOf(SessionException.class);
    }

    @Test
    void failedLoginLeavesSessionLoggedOut() {
        AtmSession broken = new AtmSession(new BankService(new FailingLedgerStore()));

        assertThatThrownBy(() -> broken.login(ALICE)).isInstanceOf(IllegalStateException.class);
        assertThat(broken.currentCustomer()).isEmpty();
    }

    @Test
    void transferToUnknownCustomerIsRejected() {
        session.login(ALICE);

        assertThatThrownBy(() -> session.transfer(CustomerName.of("Ghost"), Money.of(1)))
                .isInstanceOf(UnknownCustomerException.class);
    }
}
