package io.github.bluething.atmsimulator.application;

import io.github.bluething.atmsimulator.domain.Ledger;
import io.github.bluething.atmsimulator.domain.LedgerView;

import java.util.function.Function;

/** Test double for an infrastructure failure, e.g. a database that is down. */
public final class FailingLedgerStore implements LedgerStore {

    @Override
    public <R> R read(Function<? super LedgerView, ? extends R> query) {
        throw new IllegalStateException("store unavailable");
    }

    @Override
    public <R> R update(Function<? super Ledger, ? extends R> update) {
        throw new IllegalStateException("store unavailable");
    }
}
