package io.github.bluething.indexer;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Splits text into words.
 * <p>
 * A word is a run of letters/digits (plus combining marks such as accents). An apostrophe or hyphen
 * between two such runs is kept, so "don't" and "state-of-the-art" are one word each.
 * Everything else is a separator.
 */
public final class Tokenizer {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*(?:['’-][\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*)*");

    private Tokenizer() {
    }

    /** Returns words in document order, duplicates included. */
    public static List<String> words(String text) {
        // NFC so "é" typed as e + combining accent is the same word (and length) as the precomposed "é"
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        return WORD.matcher(normalized).results().map(MatchResult::group).toList();
    }
}
