package io.github.bluething.searchindexer.indexing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Indexes many files in parallel while delivering reports in the order the files were given.
 *
 * <h2>Why a sliding window</h2>
 * Submitting every file up front would let finished reports pile up behind one slow file. Instead at most
 * {@code 2 x parallelism} files are in flight: enough to keep all workers busy while the head of the queue is
 * being written, and a hard bound on open files and buffered results. See ADR 0003.
 *
 * <h2>Why platform threads</h2>
 * Tokenizing is CPU-bound, so a pool sized to the CPU count is the right tool; virtual threads help when tasks
 * mostly wait.
 *
 * <h2>Cancellation</h2>
 * If the sink throws or the calling thread is interrupted, every in-flight task is cancelled with interruption
 * and the pool is closed before this method returns. Plain-text indexing stops promptly (file channels and the
 * tokenizer both honour interruption); HTML parsing runs to completion, bounded by the HTML size limit.
 */
public final class BatchIndexer {

    /** Far above any useful value; keeps the window arithmetic and thread count sane. */
    public static final int MAX_PARALLELISM = 1024;

    private final DocumentIndexer indexer;
    private final int parallelism;

    public BatchIndexer(DocumentIndexer indexer, int parallelism) {
        if (parallelism < 1 || parallelism > MAX_PARALLELISM) {
            throw new IllegalArgumentException(
                    "parallelism must be in [1, " + MAX_PARALLELISM + "], was " + parallelism);
        }
        this.indexer = Objects.requireNonNull(indexer, "indexer");
        this.parallelism = parallelism;
    }

    /**
     * Indexes {@code files} and passes each report to {@code sink}, in input order.
     *
     * @throws IOException          if the sink fails; remaining work is cancelled
     * @throws InterruptedException if the calling thread is interrupted; remaining work is cancelled
     */
    public void indexAll(List<Path> files, ReportSink sink) throws IOException, InterruptedException {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(sink, "sink");
        int window = 2 * parallelism; // cannot overflow: parallelism <= MAX_PARALLELISM
        var threads = Thread.ofPlatform().name("indexer-", 1).daemon(true).factory();

        // ExecutorService is AutoCloseable since Java 19: close() waits for running tasks to finish.
        try (ExecutorService pool = Executors.newFixedThreadPool(parallelism, threads)) {
            Deque<Future<FileReport>> inFlight = new ArrayDeque<>(window);
            Iterator<Path> pending = files.iterator();
            try {
                fill(inFlight, pending, pool, window);
                while (!inFlight.isEmpty()) {
                    // Keep the head in the deque while waiting, so the finally block can still cancel it.
                    FileReport report = await(inFlight.peekFirst());
                    inFlight.removeFirst();
                    sink.accept(report);
                    fill(inFlight, pending, pool, window);
                }
            } finally {
                // Empty on success; on failure, stop everything still running before close() waits for it.
                inFlight.forEach(future -> future.cancel(true));
            }
        }
    }

    private void fill(Deque<Future<FileReport>> inFlight, Iterator<Path> pending, ExecutorService pool, int window) {
        while (inFlight.size() < window && pending.hasNext()) {
            Path file = pending.next();
            inFlight.addLast(pool.submit(() -> indexer.index(file)));
        }
    }

    private static FileReport await(Future<FileReport> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            // DocumentIndexer turns exceptions into reports, so only an Error (e.g. OutOfMemoryError) gets here.
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("DocumentIndexer must not throw", e.getCause());
        }
    }
}
