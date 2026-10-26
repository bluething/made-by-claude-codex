package io.github.bluething.atmsimulator.infrastructure;

import io.github.bluething.atmsimulator.application.LedgerStore;
import io.github.bluething.atmsimulator.domain.Ledger;
import io.github.bluething.atmsimulator.domain.LedgerView;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Keeps the ledger in memory using copy-on-write:
 * <ol>
 *   <li>an update takes the write lock, so updates run one at a time;</li>
 *   <li>it works on a private copy of the current ledger;</li>
 *   <li>only if it succeeds, the copy is published as the new current ledger.</li>
 * </ol>
 * A failed update simply drops its copy, so there is nothing to roll back. A published
 * ledger is frozen and never modified again, which is why reads need no lock at all, and
 * why a ledger that leaks out of a callback can't be used to bypass the lock.
 *
 * <p>Copying is O(accounts + debts) per update. That is fine for an in-memory
 * simulator; a database-backed store would use a database transaction instead.
 */
public final class InMemoryLedgerStore implements LedgerStore {

    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile Ledger current = new Ledger().freeze();

    @Override
    public <R> R read(Function<? super LedgerView, ? extends R> query) {
        return query.apply(current);
    }

    @Override
    public <R> R update(Function<? super Ledger, ? extends R> update) {
        // The lock is reentrant, so without this check a nested update would work on its
        // own copy and one of the two results would be silently lost.
        if (writeLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Nested updates are not supported; do all changes in one update");
        }
        writeLock.lock();
        try {
            Ledger working = current.copy();
            R result = update.apply(working);
            current = working.freeze();
            return result;
        } finally {
            writeLock.unlock();
        }
    }
}
