# 3. Bounded, order-preserving parallelism

## Context

Files are independent, so they can be indexed in parallel. But users and tests expect output in argument
order, and a naive "submit everything to an executor" design lets finished reports (and the executor queue)
grow without bound behind one slow file.

## Decision

`BatchIndexer` keeps a sliding window of at most `2 × parallelism` futures. The calling thread waits for the
**oldest** future, hands its report to the sink, and only then submits the next file. Workers are a fixed pool
of platform threads sized to the CPU count, because tokenizing is CPU-bound.

If the sink throws (e.g. stdout closed) or the caller is interrupted, every outstanding future, including the
one being waited on, is cancelled with interruption, and the executor is closed before the exception
propagates (`ExecutorService` is `AutoCloseable` since Java 19). Cancellation is cooperative: file channels
close on interrupt and `WordTokenizer` checks the interrupt flag between reads. HTML parsing is not
interruptible, but it is bounded by the HTML size limit.

`parallelism` is capped (1024), which also keeps the window arithmetic free of overflow.

## Consequences

- Output is deterministic: `--parallelism 1` and `--parallelism 8` produce identical bytes (tested).
- Open files and buffered results are bounded by the window size.
- One slow file stalls *output* but not correctness; the window bounds how much work queues up behind it.
- Structured concurrency (`StructuredTaskScope`) would express this more directly; it is still preview in
  Java 25.
