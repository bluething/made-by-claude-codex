# 4. Settlement rules live in the domain

Status: accepted

## Context

The interesting rules are cancelling debt on transfer, turning a shortfall into a debt, and
repaying oldest-first on deposit. Each of them reads and changes several accounts and
debts. They could live in the application service (`BankService`), in a separate
`SettlementPolicy` interface, or in the domain.

## Decision

They are methods on `Ledger`, in the domain. `BankService` only orchestrates: it opens a
store transaction, calls one `Ledger` method, builds the result, and logs.

No `SettlementPolicy` interface: there is only one policy, and an interface with a single
implementation adds indirection without any flexibility you would actually use. If a
second policy shows up (e.g. "incoming transfers also repay debts"), extracting a strategy
from `Ledger` is a small, local refactoring.

## Consequences

- To learn the business rules you read one class, and you test them with plain unit tests
  (`LedgerTest`), with no store, threads or I/O.
- `BankService` stays thin. Its tests are about transactions and results, not arithmetic.
- `Debt` is package-private: code outside the domain only sees `DebtLine` in a statement,
  so it can't change debts behind the ledger's back.
