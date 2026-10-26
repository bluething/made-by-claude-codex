package io.github.bluething.atmsimulator.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTest {

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");

    private Ledger ledger;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        ledger.openAccount(ALICE);
        ledger.openAccount(BOB);
    }

    private static Money usd(long dollars) {
        return Money.of(dollars);
    }

    @Nested
    class Accounts {

        @Test
        void newAccountStartsEmpty() {
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(Money.ZERO);
            assertThat(ledger.statementOf(ALICE))
                    .isEqualTo(new AccountStatement(ALICE, Money.ZERO, List.of(), List.of()));
        }

        @Test
        void openingAnExistingAccountKeepsItsBalance() {
            ledger.deposit(ALICE, usd(10));

            assertThat(ledger.openAccount(ALICE)).isFalse();
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(10));
        }

        @Test
        void unknownCustomerIsRejected() {
            CustomerName ghost = CustomerName.of("Ghost");

            assertThat(ledger.hasAccount(ghost)).isFalse();
            assertThatThrownBy(() -> ledger.balanceOf(ghost)).isInstanceOf(UnknownCustomerException.class);
            assertThatThrownBy(() -> ledger.deposit(ghost, usd(1))).isInstanceOf(UnknownCustomerException.class);
        }

        @Test
        void copyIsIndependent() {
            Ledger copy = ledger.copy();
            copy.deposit(ALICE, usd(10));

            assertThat(ledger.balanceOf(ALICE)).isEqualTo(Money.ZERO);
            assertThat(copy.balanceOf(ALICE)).isEqualTo(usd(10));
        }
    }

    @Nested
    class DepositAndWithdraw {

        @Test
        void depositAddsToBalance() {
            ledger.deposit(ALICE, usd(100));
            ledger.deposit(ALICE, Money.of("0.50"));

            assertThat(ledger.balanceOf(ALICE)).isEqualTo(Money.of("100.50"));
        }

        @Test
        void withdrawSubtractsFromBalance() {
            ledger.deposit(ALICE, usd(100));
            ledger.withdraw(ALICE, usd(100));

            assertThat(ledger.balanceOf(ALICE)).isEqualTo(Money.ZERO);
        }

        @Test
        void cannotWithdrawMoreThanBalance() {
            ledger.deposit(ALICE, usd(10));

            assertThatThrownBy(() -> ledger.withdraw(ALICE, Money.of("10.01")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(10));
        }

        @Test
        void amountsMustBePositive() {
            assertThatThrownBy(() -> ledger.deposit(ALICE, Money.ZERO)).isInstanceOf(InvalidAmountException.class);
            assertThatThrownBy(() -> ledger.withdraw(ALICE, Money.ZERO)).isInstanceOf(InvalidAmountException.class);
            assertThatThrownBy(() -> ledger.transfer(ALICE, BOB, Money.ZERO))
                    .isInstanceOf(InvalidAmountException.class);
        }
    }

    @Nested
    class Transfer {

        @Test
        void paysInFullWhenBalanceIsEnough() {
            ledger.deposit(BOB, usd(80));

            List<Payment> payments = ledger.transfer(BOB, ALICE, usd(50));

            assertThat(payments).containsExactly(new Payment(BOB, ALICE, usd(50)));
            assertThat(ledger.balanceOf(BOB)).isEqualTo(usd(30));
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(50));
            assertThat(ledger.statementOf(BOB).owedTo()).isEmpty();
        }

        @Test
        void shortfallBecomesDebtToTheTarget() {
            ledger.deposit(BOB, usd(30));

            List<Payment> payments = ledger.transfer(BOB, ALICE, usd(100));

            assertThat(payments).containsExactly(new Payment(BOB, ALICE, usd(30)));
            assertThat(ledger.balanceOf(BOB)).isEqualTo(Money.ZERO);
            assertThat(ledger.statementOf(BOB).owedTo()).containsExactly(new DebtLine(ALICE, usd(70)));
            assertThat(ledger.statementOf(ALICE).owedFrom()).containsExactly(new DebtLine(BOB, usd(70)));
        }

        @Test
        void withEmptyBalanceEverythingBecomesDebtAndNoMoneyMoves() {
            assertThat(ledger.transfer(BOB, ALICE, usd(10))).isEmpty();
            assertThat(ledger.statementOf(BOB).owedTo()).containsExactly(new DebtLine(ALICE, usd(10)));
        }

        @Test
        void debtToTheSameCreditorAccumulates() {
            ledger.transfer(BOB, ALICE, usd(10));
            ledger.transfer(BOB, ALICE, usd(5));

            assertThat(ledger.statementOf(BOB).owedTo()).containsExactly(new DebtLine(ALICE, usd(15)));
        }

        @Test
        void transferToDebtorCancelsTheirDebtInsteadOfMovingMoney() {
            ledger.deposit(ALICE, usd(210));
            ledger.transfer(BOB, ALICE, usd(40));

            List<Payment> payments = ledger.transfer(ALICE, BOB, usd(30));

            assertThat(payments).isEmpty();
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(210));
            assertThat(ledger.statementOf(ALICE).owedFrom()).containsExactly(new DebtLine(BOB, usd(10)));
        }

        @Test
        void transferToDebtorForMoreThanTheyOweCancelsDebtAndPaysTheRest() {
            ledger.deposit(ALICE, usd(210));
            ledger.transfer(BOB, ALICE, usd(40));

            List<Payment> payments = ledger.transfer(ALICE, BOB, usd(50));

            assertThat(payments).containsExactly(new Payment(ALICE, BOB, usd(10)));
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(200));
            assertThat(ledger.balanceOf(BOB)).isEqualTo(usd(10));
            assertThat(ledger.statementOf(ALICE).owedFrom()).isEmpty();
            assertThat(ledger.statementOf(BOB).owedTo()).isEmpty();
        }

        @Test
        void neverLeavesTwoCustomersOwingEachOther() {
            ledger.transfer(BOB, ALICE, usd(40));

            ledger.transfer(ALICE, BOB, usd(100)); // cancels 40, Alice has no cash, owes 60

            assertThat(ledger.statementOf(ALICE).owedTo()).containsExactly(new DebtLine(BOB, usd(60)));
            assertThat(ledger.statementOf(ALICE).owedFrom()).isEmpty();
        }

        @Test
        void receivingATransferDoesNotRepayTheReceiversDebts() {
            CustomerName carol = CustomerName.of("Carol");
            ledger.openAccount(carol);
            ledger.transfer(BOB, carol, usd(20));   // Bob owes Carol 20
            ledger.deposit(ALICE, usd(50));

            ledger.transfer(ALICE, BOB, usd(50));

            assertThat(ledger.balanceOf(BOB)).isEqualTo(usd(50));
            assertThat(ledger.statementOf(BOB).owedTo()).containsExactly(new DebtLine(carol, usd(20)));
        }

        @Test
        void rejectsSelfTransfer() {
            ledger.deposit(ALICE, usd(10));

            assertThatThrownBy(() -> ledger.transfer(ALICE, ALICE, usd(5)))
                    .isInstanceOf(TransferNotAllowedException.class);
        }

        @Test
        void rejectsUnknownSender() {
            assertThatThrownBy(() -> ledger.transfer(CustomerName.of("Ghost"), ALICE, usd(5)))
                    .isInstanceOf(UnknownCustomerException.class);
        }

        @Test
        void rejectsUnknownTarget() {
            assertThatThrownBy(() -> ledger.transfer(ALICE, CustomerName.of("Ghost"), usd(5)))
                    .isInstanceOf(UnknownCustomerException.class);
        }
    }

    @Nested
    class Repayment {

        private final CustomerName carol = CustomerName.of("Carol");

        @BeforeEach
        void openCarol() {
            ledger.openAccount(carol);
        }

        @Test
        void depositWithoutDebtsMakesNoPayments() {
            assertThat(ledger.deposit(BOB, usd(10))).isEmpty();
        }

        @Test
        void depositRepaysAsMuchAsItCan() {
            ledger.transfer(BOB, ALICE, usd(40));

            List<Payment> repayments = ledger.deposit(BOB, usd(30));

            assertThat(repayments).containsExactly(new Payment(BOB, ALICE, usd(30)));
            assertThat(ledger.balanceOf(BOB)).isEqualTo(Money.ZERO);
            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(30));
            assertThat(ledger.statementOf(BOB).owedTo()).containsExactly(new DebtLine(ALICE, usd(10)));
        }

        @Test
        void depositRepaysOldestDebtFirstAndKeepsTheChange() {
            ledger.transfer(BOB, carol, usd(20));
            ledger.transfer(BOB, ALICE, usd(20));

            List<Payment> repayments = ledger.deposit(BOB, usd(50));

            assertThat(repayments).containsExactly(
                    new Payment(BOB, carol, usd(20)),
                    new Payment(BOB, ALICE, usd(20)));
            assertThat(ledger.balanceOf(BOB)).isEqualTo(usd(10));
            assertThat(ledger.statementOf(BOB).owedTo()).isEmpty();
        }

        @Test
        void increasingADebtKeepsItsPlaceInTheQueue() {
            ledger.transfer(BOB, carol, usd(10));
            ledger.transfer(BOB, ALICE, usd(10));
            ledger.transfer(BOB, carol, usd(10)); // still the oldest

            assertThat(ledger.deposit(BOB, usd(20)))
                    .containsExactly(new Payment(BOB, carol, usd(20)));
        }

        @Test
        void aDebtOpenedAgainAfterBeingPaidOffGoesToTheBackOfTheQueue() {
            ledger.transfer(BOB, carol, usd(10));
            ledger.transfer(BOB, ALICE, usd(10));
            ledger.deposit(BOB, usd(10));         // pays off Carol
            ledger.transfer(BOB, carol, usd(10)); // new debt to Carol, now the newest

            assertThat(ledger.deposit(BOB, usd(10)))
                    .containsExactly(new Payment(BOB, ALICE, usd(10)));
        }

        @Test
        void repaysWithTheWholeBalanceNotOnlyTheDeposit() {
            ledger.transfer(BOB, ALICE, usd(30));
            ledger.deposit(carol, usd(20));
            ledger.transfer(carol, BOB, usd(20)); // Bob holds 20 and still owes 30

            assertThat(ledger.deposit(BOB, usd(5)))
                    .containsExactly(new Payment(BOB, ALICE, usd(25)));
            assertThat(ledger.statementOf(BOB).owedTo()).containsExactly(new DebtLine(ALICE, usd(5)));
        }

        @Test
        void creditorReceivingARepaymentDoesNotRepayTheirOwnDebts() {
            ledger.transfer(ALICE, carol, usd(15)); // Alice owes Carol 15
            ledger.transfer(BOB, ALICE, usd(15));   // Bob owes Alice 15

            ledger.deposit(BOB, usd(15));

            assertThat(ledger.balanceOf(ALICE)).isEqualTo(usd(15));
            assertThat(ledger.statementOf(ALICE).owedTo()).containsExactly(new DebtLine(carol, usd(15)));
        }

        @Test
        void statementListsDebtsOldestFirst() {
            ledger.transfer(BOB, carol, usd(1));
            ledger.transfer(BOB, ALICE, usd(2));
            ledger.transfer(carol, ALICE, usd(3));

            assertThat(ledger.statementOf(BOB).owedTo())
                    .containsExactly(new DebtLine(carol, usd(1)), new DebtLine(ALICE, usd(2)));
            assertThat(ledger.statementOf(ALICE).owedFrom())
                    .containsExactly(new DebtLine(BOB, usd(2)), new DebtLine(carol, usd(3)));
        }
    }
}
