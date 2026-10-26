# 3. One Ledger aggregate behind a copy-on-write store

Status: accepted

## Context

A single operation changes several things at once. A deposit can credit the customer,
then pay three creditors and close three debts. If something fails halfway, no customer
may end up with money that came from nowhere or disappeared. The bank is also meant to
be shared by many ATMs, so operations must not interleave.

Alternatives considered:

1. **Separate `AccountRepository` and `DebtRepository` with a `TransactionManager`
   that snapshots both and restores them on failure.** This works, but it means two
   repositories to coordinate, restore logic to get right, and more interfaces than the
   problem needs.
2. **Mutable entities with a lock on each account.** Locks would have to be taken in a
   consistent order to avoid deadlocks, and a failure would still leave partial changes.

## Decision

- All the state (balances, debts and the debt sequence counter) lives in **one aggregate,
  `Ledger`**. Every rule that involves more than one account is a method on it.
- The application gets the ledger through **one port, `LedgerStore`**, which has
  `read(query)` and `update(function)`.
- `InMemoryLedgerStore` implements `update` as **copy-on-write**: it takes the lock, copies
  the current ledger, runs the function on the copy, and makes the copy current only if the
  function returns. If it throws, the copy is discarded, so there is nothing to roll back.
- A ledger that has been made current is **frozen** and never changed again, so `read`
  needs no lock: a `volatile` read is enough to see a complete, consistent ledger.
  Freezing also means a ledger that leaks out of a callback (`update(l -> l)`, or a cast of
  the `LedgerView`) can't be used to change state behind the lock.
- **Nested updates are rejected.** With a reentrant lock, an inner update would publish its
  own copy, and the outer update would then overwrite it, losing a write.

## Consequences

- Every use case is atomic and isolated by construction, and the code says so in one place.
- Reads don't wait for writes.
- Each update copies the ledger: O(accounts + debts). This is fine in memory. A database
  adapter would replace copy-on-write with a real transaction, and the application and
  domain code would stay the same.
- Updates run one at a time across the whole bank. For an in-memory simulator, simplicity
  wins over throughput.
- Proved by `InMemoryLedgerStoreTest`: money is conserved under 16 concurrent threads, and
  the test fails if the lock is removed.
