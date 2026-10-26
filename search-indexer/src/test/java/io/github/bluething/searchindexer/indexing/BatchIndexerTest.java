package io.github.bluething.searchindexer.indexing;

import io.github.bluething.searchindexer.document.ContentExtractor;
import io.github.bluething.searchindexer.document.ContentExtractors;
import io.github.bluething.searchindexer.rule.IndexingRules;
import io.github.bluething.searchindexer.text.WordTokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concurrency tests coordinate threads with latches, never with sleeps: each test forces the interleaving it
 * claims to test, so it cannot pass by luck or fail on a slow machine. The class-level timeout turns a
 * hang (the usual symptom of a concurrency bug) into a failure.
 */
@Timeout(10)
class BatchIndexerTest {

    private static final long LATCH_TIMEOUT_SECONDS = 5;

    @TempDir
    Path dir;

    @Test
    void deliversReportsInInputOrderEvenWhenLaterFilesFinishFirst() throws Exception {
        List<Path> files = createFiles(8);
        Path first = files.getFirst();
        // parallelism 4 -> window 8: all files are in flight, and file 1 waits until the other 7 are done.
        var othersDone = new CountDownLatch(files.size() - 1);
        var extractor = new TrackingExtractor() {
            @Override
            void beforeOpen(Path file) throws IOException {
                if (file.equals(first)) {
                    await(othersDone);
                }
            }

            @Override
            void afterClose(Path file) {
                if (!file.equals(first)) {
                    othersDone.countDown();
                }
            }
        };

        var delivered = new ArrayList<Path>();
        new BatchIndexer(indexerUsing(extractor), 4).indexAll(files, report -> delivered.add(report.file()));

        assertThat(delivered).containsExactlyElementsOf(files);
    }

    @Test
    void neverOpensMoreFilesAtOnceThanTheParallelismAndClosesThemAll() throws Exception {
        List<Path> files = createFiles(30);
        var extractor = new TrackingExtractor();

        new BatchIndexer(indexerUsing(extractor), 3).indexAll(files, report -> { });

        assertThat(extractor.maxOpen.get()).isBetween(1, 3);
        assertThat(extractor.opened.get()).isEqualTo(30);
        assertThat(extractor.open.get()).as("readers still open").isZero();
    }

    @Test
    void startsNoMoreThanTheWindowWhileTheHeadIsBlocked() throws Exception {
        List<Path> files = createFiles(50);
        Path first = files.getFirst();
        int parallelism = 2;
        int window = 2 * parallelism;
        // File 1 blocks until the rest of the window has been indexed, then records how many files were started.
        var restOfWindowDone = new CountDownLatch(window - 1);
        var startedWhileHeadBlocked = new AtomicInteger(-1);
        var extractor = new TrackingExtractor() {
            @Override
            void beforeOpen(Path file) throws IOException {
                if (file.equals(first)) {
                    await(restOfWindowDone);
                    startedWhileHeadBlocked.set(opened.get());
                }
            }

            @Override
            void afterClose(Path file) {
                restOfWindowDone.countDown();
            }
        };

        new BatchIndexer(indexerUsing(extractor), parallelism).indexAll(files, report -> { });

        // Nothing beyond the window can start until the head is delivered.
        assertThat(startedWhileHeadBlocked.get()).isEqualTo(window - 1);
    }

    @Test
    void sinkFailureCancelsWorkInProgressAndPropagates() throws Exception {
        List<Path> files = createFiles(10);
        Path blocked = files.get(1);
        var blockedStarted = new CountDownLatch(1);
        var neverReleased = new CountDownLatch(1);
        var interrupted = ConcurrentHashMap.<Path>newKeySet();
        var extractor = new TrackingExtractor() {
            @Override
            void beforeOpen(Path file) throws IOException {
                if (file.equals(blocked)) {
                    blockedStarted.countDown();
                    awaitRecordingInterrupt(neverReleased, file, interrupted);
                }
            }
        };

        assertThatThrownBy(() -> new BatchIndexer(indexerUsing(extractor), 2).indexAll(files, report -> {
            await(blockedStarted); // make sure there is work in progress to cancel
            throw new IOException("broken pipe");
        })).isInstanceOf(IOException.class).hasMessage("broken pipe");

        // indexAll returned, so the pool is closed: the blocked task must have been interrupted, not awaited.
        assertThat(interrupted).containsExactly(blocked);
        assertThat(extractor.open.get()).as("readers still open").isZero();
    }

    @Test
    void interruptingTheCallerCancelsWorkInProgress() throws Exception {
        List<Path> files = createFiles(3);
        Path head = files.getFirst();
        var headStarted = new CountDownLatch(1);
        var neverReleased = new CountDownLatch(1);
        var interrupted = ConcurrentHashMap.<Path>newKeySet();
        var extractor = new TrackingExtractor() {
            @Override
            void beforeOpen(Path file) throws IOException {
                if (file.equals(head)) {
                    headStarted.countDown();
                    awaitRecordingInterrupt(neverReleased, file, interrupted);
                }
            }
        };
        var outcome = new AtomicReference<Throwable>();

        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                new BatchIndexer(indexerUsing(extractor), 2).indexAll(files, report -> { });
            } catch (Throwable t) {
                outcome.set(t);
            }
        });
        await(headStarted);
        caller.interrupt();
        caller.join(TimeUnit.SECONDS.toMillis(LATCH_TIMEOUT_SECONDS));

        assertThat(caller.isAlive()).as("caller still blocked").isFalse();
        assertThat(outcome.get()).isInstanceOf(InterruptedException.class);
        assertThat(interrupted).containsExactly(head);
    }

    @Test
    void emptyInputDeliversNothing() throws Exception {
        var delivered = new AtomicInteger();

        new BatchIndexer(indexerUsing(new TrackingExtractor()), 2)
                .indexAll(List.of(), report -> delivered.incrementAndGet());

        assertThat(delivered).hasValue(0);
    }

    @Test
    void rejectsParallelismOutOfRange() {
        var indexer = indexerUsing(new TrackingExtractor());

        assertThatThrownBy(() -> new BatchIndexer(indexer, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchIndexer(indexer, BatchIndexer.MAX_PARALLELISM + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Path> createFiles(int count) throws IOException {
        var files = new ArrayList<Path>();
        for (int i = 0; i < count; i++) {
            files.add(Files.writeString(dir.resolve("file-" + i + ".txt"), "Word " + i));
        }
        return files;
    }

    private static DocumentIndexer indexerUsing(ContentExtractor extractor) {
        return new DocumentIndexer(
                new ContentExtractors(List.of(), extractor), new WordTokenizer(), IndexingRules.defaults());
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IOException("test latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted while waiting");
        }
    }

    private static void awaitRecordingInterrupt(CountDownLatch latch, Path file, Set<Path> interrupted)
            throws IOException {
        try {
            await(latch);
        } catch (InterruptedIOException e) {
            interrupted.add(file);
            throw e;
        }
    }

    /** Serves each file's own content and records how many readers are open, at most and in total. */
    private static class TrackingExtractor implements ContentExtractor {

        final AtomicInteger opened = new AtomicInteger();
        final AtomicInteger open = new AtomicInteger();
        final AtomicInteger maxOpen = new AtomicInteger();

        void beforeOpen(Path file) throws IOException {
        }

        void afterClose(Path file) {
        }

        @Override
        public boolean supports(Path file) {
            return true;
        }

        @Override
        public Reader open(Path file) throws IOException {
            beforeOpen(file);
            String content = Files.readString(file);
            opened.incrementAndGet();
            maxOpen.accumulateAndGet(open.incrementAndGet(), Math::max);
            return new StringReader(content) {
                @Override
                public void close() {
                    super.close();
                    open.decrementAndGet();
                    afterClose(file);
                }
            };
        }
    }
}
