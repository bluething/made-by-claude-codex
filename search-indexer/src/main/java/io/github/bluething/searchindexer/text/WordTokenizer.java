package io.github.bluething.searchindexer.text;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Splits a character stream into words without loading it into memory.
 *
 * <h2>What counts as a word</h2>
 * <ul>
 *   <li>A word starts with a letter or digit, followed by letters, digits or combining marks
 *       (so {@code e + U+0301} stays inside the word).</li>
 *   <li>A single apostrophe ({@code '}, {@code ’}) or hyphen between two word characters joins them:
 *       {@code don't} and {@code state-of-the-art} are one word each. A joiner at either end of a word,
 *       or two joiners in a row, is a separator.</li>
 *   <li>Every other character is a separator.</li>
 *   <li>Words are NFC-normalised, so precomposed and decomposed accents produce the same word.</li>
 *   <li>Words longer than {@code maxWordLength} UTF-16 units <em>after</em> normalisation are dropped (default
 *       {@value #DEFAULT_MAX_WORD_LENGTH}, as in Lucene's {@code StandardTokenizer}). Such "words" are almost
 *       always encoded data such as base64 or minified code. Measuring after normalisation means canonically
 *       equivalent text is treated identically.</li>
 *   <li>Separately, at most {@code 4 x maxWordLength} raw characters of one word are buffered; anything longer
 *       cannot normalise down to the limit and is discarded without being stored. This bounds memory on input
 *       that has no separators at all.</li>
 * </ul>
 *
 * <p>Tokenizing checks for thread interruption between reads and stops with {@link InterruptedIOException},
 * so a cancelled task does not keep scanning a large file.
 *
 * <p>Instances are immutable and thread-safe; all per-call state lives on the stack of {@link #tokenize}.
 */
public final class WordTokenizer {

    public static final int DEFAULT_MAX_WORD_LENGTH = 255;

    /**
     * How much longer than the limit a word may be before normalisation. Composition shrinks text by at most 3:1
     * in practice (Hangul jamo L+V+T to one syllable); 4 leaves headroom.
     */
    private static final int RAW_LENGTH_FACTOR = 4;

    private static final int BUFFER_SIZE = 8192;

    private final int maxWordLength;

    public WordTokenizer() {
        this(DEFAULT_MAX_WORD_LENGTH);
    }

    public WordTokenizer(int maxWordLength) {
        if (maxWordLength < 1 || maxWordLength > Integer.MAX_VALUE / RAW_LENGTH_FACTOR) {
            throw new IllegalArgumentException("maxWordLength out of range: " + maxWordLength);
        }
        this.maxWordLength = maxWordLength;
    }

    /**
     * Reads {@code reader} to the end and passes each word, in order, to {@code sink}.
     * The reader is not closed; the caller owns it.
     */
    public void tokenize(Reader reader, Consumer<String> sink) throws IOException {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(sink, "sink");

        var word = new WordBuilder(maxWordLength, sink);
        char[] buffer = new char[BUFFER_SIZE];
        char pendingHighSurrogate = 0;

        int read;
        while ((read = reader.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("tokenizing interrupted");
            }
            for (int i = 0; i < read; i++) {
                char c = buffer[i];
                // A surrogate pair can be split across two reads, so carry the high half over.
                if (pendingHighSurrogate != 0) {
                    char high = pendingHighSurrogate;
                    pendingHighSurrogate = 0;
                    if (Character.isLowSurrogate(c)) {
                        word.accept(Character.toCodePoint(high, c));
                        continue;
                    }
                    word.accept(high); // unpaired: treated like any other non-word character
                }
                if (Character.isHighSurrogate(c)) {
                    pendingHighSurrogate = c;
                } else {
                    word.accept(c);
                }
            }
        }
        if (pendingHighSurrogate != 0) {
            word.accept(pendingHighSurrogate);
        }
        word.finish();
    }

    /** The tokenizer's state machine: accumulates one word at a time. */
    private static final class WordBuilder {

        private final int maxLength;
        private final int maxRawLength;
        private final Consumer<String> sink;
        private final StringBuilder current = new StringBuilder(32);
        private int pendingJoiner = -1;
        private boolean overflowed;

        WordBuilder(int maxLength, Consumer<String> sink) {
            this.maxLength = maxLength;
            this.maxRawLength = maxLength * RAW_LENGTH_FACTOR;
            this.sink = sink;
        }

        void accept(int codePoint) {
            if (isWordStart(codePoint) || (inWord() && isWordContinuation(codePoint))) {
                if (pendingJoiner != -1) {
                    append(pendingJoiner);
                    pendingJoiner = -1;
                }
                append(codePoint);
            } else if (isJoiner(codePoint) && inWord() && pendingJoiner == -1) {
                pendingJoiner = codePoint;
            } else {
                finish();
            }
        }

        /** Emits the word in progress, if any, and resets. A trailing joiner is dropped. */
        void finish() {
            if (inWord() && !overflowed) {
                String word = normalize(current.toString());
                if (word.length() <= maxLength) {
                    sink.accept(word);
                }
            }
            current.setLength(0);
            pendingJoiner = -1;
            overflowed = false;
        }

        private boolean inWord() {
            return !current.isEmpty() || overflowed;
        }

        private void append(int codePoint) {
            if (overflowed) {
                return;
            }
            if (current.length() + Character.charCount(codePoint) > maxRawLength) {
                overflowed = true;
                current.setLength(0);
                return;
            }
            current.appendCodePoint(codePoint);
        }

        private static boolean isWordStart(int codePoint) {
            return Character.isLetterOrDigit(codePoint);
        }

        private static boolean isWordContinuation(int codePoint) {
            int type = Character.getType(codePoint);
            return type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
        }

        private static boolean isJoiner(int codePoint) {
            return codePoint == '\'' || codePoint == '’' || codePoint == '-';
        }

        private static String normalize(String word) {
            // isNormalized is a fast scan; most words are ASCII and skip the allocation entirely.
            return Normalizer.isNormalized(word, Normalizer.Form.NFC)
                    ? word
                    : Normalizer.normalize(word, Normalizer.Form.NFC);
        }
    }
}
