package io.github.bluething.atmsimulator.application;

import io.github.bluething.atmsimulator.domain.Ledger;
import io.github.bluething.atmsimulator.domain.LedgerView;

import java.util.function.Function;

/**
 * Owns the {@link Ledger} and decides how it is kept safe. This is a <i>port</i>: the
 * application states what it needs, and an adapter in {@code infrastructure} provides it
 * (in memory today, a database tomorrow, without touching the application or the domain).
 *
 * <p>Implementations must be thread safe.
 */
public interface LedgerStore {

    /**
     * Runs a query against a consistent view of the ledger.
     * The query must not try to change the ledger.
     */
    <R> R read(Function<? super LedgerView, ? extends R> query);

    /**
     * Runs {@code update} as one transaction: if it returns, all of its changes become
     * visible together; if it throws, none of them do. Concurrent updates never interleave.
     */
    <R> R update(Function<? super Ledger, ? extends R> update);
}
