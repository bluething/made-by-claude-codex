# atm-simulator

A command line ATM that talks to an in-memory retail bank. Customers log in, deposit,
withdraw and transfer money. If you transfer more than you have, the rest becomes a debt
that is paid back automatically from your next deposit.

It solves the problem in [`../test_ajaib_atm.md`](../test_ajaib_atm.md), and it is meant
to be read: it shows how a small Java service can be structured, tested and documented
without a framework. The [quick version](../atm) solves the same problem in three classes,
so you can compare the two.

## Quick start

Requirements: **JDK 25**. Maven isn't needed because the wrapper (`./mvnw`) downloads it.

```bash
./start.sh                  # build, then start an interactive session
./mvnw verify               # tests + architecture rules + coverage gate
./scripts/smoke-test.sh     # runs the example session through start.sh
```

Every start begins with an empty bank. Build output goes to stderr, so stdout contains
only the session and you can pipe commands in: `./start.sh < commands.txt`.

## Commands

| Command | What it does |
|---|---|
| `login [name]` | Logs in as this customer and creates the customer if not exist |
| `deposit [amount]` | Deposits, then pays back your debts, oldest first |
| `withdraw [amount]` | Withdraws; rejected if your balance is not enough |
| `transfer [target] [amount]` | Transfers to another customer (see rules below) |
| `logout` | Logs out |
| `help` | Lists the commands |
| `exit` | Logs out if needed and ends the session (end of input does the same) |

Amounts are positive with up to 2 decimals (`100`, `12.50`). Names are 1-32 characters
from `A-Z a-z 0-9 _ -` and are case sensitive. Command keywords are case insensitive.

```
$ login Bob
Hello, Bob!
Your balance is $30

$ transfer Alice 100
Transferred $30 to Alice
Your balance is $0
Owed $70 to Alice
```

## Business rules

All money rules live in one class, [`Ledger`](src/main/java/io/github/bluething/atmsimulator/domain/Ledger.java).

**transfer(from, to, amount)**
1. If `to` owes `from`, that debt is cancelled first, without moving money.
2. The rest is paid from `from`'s balance, as far as it goes.
3. Whatever is still unpaid becomes a debt from `from` to `to` (added to an existing one).

**deposit(customer, amount)** adds to the balance, then uses the *whole* balance to pay
back the customer's debts, oldest first.

**withdraw(customer, amount)** never creates a debt. It is rejected if the balance is too low.

Invariants: balances are never negative, debts are always positive, and two customers
never owe each other at the same time.

### Assumptions (not stated in the spec)

| Question | Decision | Why |
|---|---|---|
| Does receiving a transfer pay back the receiver's debts? | No, only a deposit does | It's the only behaviour the spec shows, and it avoids chains of repayments |
| Repayment order with several creditors? | Oldest debt first. Adding to a debt keeps its place in line; a debt that was paid off and opened again goes to the back | Fair and predictable |
| Transfer to a customer who doesn't exist? | Rejected | A typo shouldn't create an account and send money to it |
| Login while already logged in? | Rejected, you must log out first | One customer per ATM at a time |
| Cents? | Supported, up to 2 decimals; whole dollars print as `$100`, as in the spec | Real money has cents |

## Architecture

Ports and adapters (hexagonal) in a single Maven module. Each arrow means "depends on",
and the [`ArchitectureTest`](src/test/java/io/github/bluething/atmsimulator/ArchitectureTest.java)
fails the build if code breaks this direction.

```
              ┌──────────────────────── AtmApplication (composition root) ──┐
              ▼                                   ▼                         ▼
   cli  (adapter: console)         infrastructure  (adapter: storage)       │
   AtmConsole, CommandParser,      InMemoryLedgerStore                      │
   ConsolePresenter, Command               │  implements                    │
              │                            ▼                                │
              └──────────────►  application  ◄──────────────────────────────┘
                                BankService, AtmSession,
                                LedgerStore (port), OperationResult
                                           │
                                           ▼
                                domain  (JDK only)
                                Ledger, Money, CustomerName, Debt, ...
```

| Package | Responsibility | Knows about |
|---|---|---|
| `domain` | Business rules and value objects. No I/O, no threads | the JDK only |
| `application` | Use cases (one transaction each), ATM session, the `LedgerStore` port | domain |
| `infrastructure` | `LedgerStore` implementation: in memory, copy-on-write | application, domain |
| `cli` | Parse input, call the session, render output | application, domain |
| root | `AtmApplication` wires everything by hand | everything |

### What happens when you type `deposit 30`

```
AtmConsole.run          reads the line
 └ CommandParser.parse  -> Command.Deposit(Money 30.00)        invalid input is rejected here
 └ AtmSession.deposit   checks someone is logged in
   └ BankService.deposit
     └ LedgerStore.update(ledger -> ...)                       one atomic transaction
       └ Ledger.deposit       credit, then repay debts oldest first
       └ Ledger.statementOf   statement from the same transaction
 └ ConsolePresenter.operation  -> "Transferred $30 to Alice", "Your balance is $0", ...
```

### Key decisions

Each decision has a short record in [`docs/adr`](docs/adr):

1. [No framework, manual wiring](docs/adr/0001-no-framework.md): a dozen objects don't need a DI container.
2. [Money as a normalised `BigDecimal`](docs/adr/0002-money.md): exact, never rounds, and can't overflow.
3. [One `Ledger` aggregate behind a copy-on-write store](docs/adr/0003-ledger-and-copy-on-write.md):
   each operation is atomic and thread safe, with no rollback code.
4. [Rules live in the domain, not in services](docs/adr/0004-rules-in-the-domain.md).

## Error handling

| Kind | Example | Behaviour |
|---|---|---|
| Invalid input | `deposit abc`, `fly` | Message with the correct usage; session continues |
| Business rule | not enough balance, not logged in | `BusinessException`; its message is shown as-is |
| Unexpected bug | a `NullPointerException` | Logged to stderr (without the raw input), a generic message is shown, and the session continues. This is safe because the failed operation changed nothing |
| Console I/O | stdin can't be read | Logged; the process exits with code 1 |

Logging uses the JDK's `System.Logger`, with no extra dependency. Operations log at DEBUG,
so stderr stays quiet in normal use.

## Testing

| Level | Where | What it proves |
|---|---|---|
| Unit | `domain/*Test` | Every money rule and edge case, with no infrastructure involved |
| Concurrency | `InMemoryLedgerStoreTest` | 16 threads x 500 random operations conserve money; a failed update leaves no trace. (Confirmed: removing the lock makes this test fail) |
| Use case | `application/*Test` | Session rules; results come from the same transaction |
| CLI | `cli/*Test` | Parsing, exact wording, error recovery, exit / end of input |
| Acceptance | `ExampleSessionTest` + `scripts/smoke-test.sh` | The spec's example session, in-process and through the real `start.sh` |
| Architecture | `ArchitectureTest` | Dependencies point the right way. (Confirmed: a domain -> application import fails the build) |

Quality gates in `mvn verify`, which also run in [CI](../.github/workflows/atm-simulator.yml):
- `javac -Xlint:all -Werror`
- the enforcer plugin requires JDK 25+ and Maven 3.9+, and checks dependency convergence
- JaCoCo requires at least 90% line coverage (currently about 97%)

## How to extend

**Add a command**, e.g. `balance`:
1. Add `BALANCE("balance", "", "Shows your balance")` to `CommandSyntax`.
2. Add `record Balance() implements Command` to `Command`.
3. Compile. The exhaustive `switch` statements in `CommandParser` and `AtmConsole` now fail
   to compile until you handle the new case. The compiler shows you every place to change.
4. Add a use case on `AtmSession` / `BankService` if it needs data, plus tests.

**Store data in a database:** implement `LedgerStore` with a real transaction (load,
apply, save, commit) in `infrastructure`, then swap it in `AtmApplication`. The domain,
the use cases and their tests stay the same.

## Not in scope (yet)

- Persistence across restarts (the spec requires a fresh bank on every run)
- Authentication: anyone can log in as anyone, as in the spec
- Audit trail / transaction history, multiple currencies, per-day limits
- Copy-on-write costs O(accounts + debts) per update. That is fine for a simulator, but a
  real bank would use row-level locking in a database.
