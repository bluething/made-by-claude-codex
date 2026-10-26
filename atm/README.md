# atm-cli

CLI that simulates an ATM talking to a retail bank. Problem statement: `../test_ajaib_atm.md`.

## Run

```bash
./start.sh
```

Needs only Java 25. Maven comes from the wrapper (`./mvnw`), so the first run downloads it.
Build output goes to stderr, so stdout only has the ATM session.
State is kept in memory, so every run starts empty.

Tests:

```bash
./mvnw test
```

## Commands

| Command | |
|---|---|
| `login [name]` | log in, creates the customer if needed |
| `deposit [amount]` | deposit, then pay back debts |
| `withdraw [amount]` | withdraw, can't go below 0 |
| `transfer [target] [amount]` | transfer to another customer |
| `logout` | log out |

## Code

```
Main  -> wires stdin/stdout
Atm   -> parses commands, holds who is logged in, prints output
Bank  -> balances, debts and all the money rules
```

`Bank` knows nothing about the console and returns plain values (`Payment`, `Debt`),
so it can be tested on its own. `Atm` has no money rules. `ExampleSessionTest`
replays the example session from the problem statement.

## Rules

- **transfer**: if the target owes me, cancel that debt first, no money moves.
  The rest is paid from my balance. Whatever I can't pay becomes a debt I owe the target.
- **deposit**: add to balance, then pay back my debts, oldest first, as far as the balance goes.
- **withdraw**: rejected if the balance is not enough. A withdraw never creates a debt.

## Assumptions

Things the spec does not say, and what I picked:

- Amounts are positive whole dollars (all examples are integers), stored as `long`.
  One operation is capped at $1,000,000,000, like an ATM limit. That keeps totals far from
  `Long.MAX_VALUE`, so a deposit that pays several creditors can't overflow halfway through.
- Only a deposit pays back debts. Money received from a transfer stays in the balance.
  The example only shows repayment on deposit.
- `transfer` to an unknown customer is rejected, it does not create the customer.
  Transfer to yourself is rejected.
- Names are case sensitive, `alice` and `Alice` are different customers.
- `login` while already logged in is rejected, you need to `logout` first.
- Command names are case insensitive.

## What I would do with more time

- Money as `BigDecimal` (or cents in a `long`) so cents are supported.
- A `Repository` interface in front of the maps if it needs real storage.
  Today that would be an interface with one implementation, so I left it out.
- Thread safety if more than one ATM shares a bank. Right now it is single threaded on purpose.
- Transaction history / statement command.
