package io.github.bluething.searchindexer.rule;

/**
 * Per-document state of an {@link IndexingRule}.
 *
 * <p>Lifecycle: created by {@link IndexingRule#newAccumulator()} for exactly one document, used by exactly one
 * thread. {@link #accept} is called once per word in document order (possibly zero times for an empty document),
 * then {@link #result} is called once. Implementations therefore need no synchronisation, must not share mutable
 * state with other accumulators, and may assume {@code accept} is never called after {@code result}.
 *
 * <p>If extraction fails part-way through a document, the accumulator is discarded and {@code result} is never
 * called: partial results are never reported.
 */
public interface WordAccumulator {

    /** @param word a non-empty, NFC-normalised word as produced by the tokenizer */
    void accept(String word);

    RuleValue result();
}
