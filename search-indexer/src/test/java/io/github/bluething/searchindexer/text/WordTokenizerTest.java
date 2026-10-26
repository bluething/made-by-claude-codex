package io.github.bluething.searchindexer.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WordTokenizerTest {

    private final WordTokenizer tokenizer = new WordTokenizer();

    static Stream<Arguments> examples() {
        return Stream.of(
                Arguments.of("plain sentence", "Hello, world! How are you?", List.of("Hello", "world", "How", "are", "you")),
                Arguments.of("duplicates kept in order", "the cat the hat", List.of("the", "cat", "the", "hat")),
                Arguments.of("inner apostrophe and hyphen", "don't state-of-the-art", List.of("don't", "state-of-the-art")),
                Arguments.of("typographic apostrophe", "it’s", List.of("it’s")),
                Arguments.of("joiners at the edges are separators", "'quoted' -dash-", List.of("quoted", "dash")),
                Arguments.of("double joiner splits", "well--known rock''n", List.of("well", "known", "rock", "n")),
                Arguments.of("digits are word characters", "Java 25 in 2025", List.of("Java", "25", "in", "2025")),
                Arguments.of("other punctuation splits", "a.b,c_d/e", List.of("a", "b", "c", "d", "e")),
                Arguments.of("unicode letters", "Ångström café Straße 東京", List.of("Ångström", "café", "Straße", "東京")),
                Arguments.of("combining mark cannot start a word", "́abc", List.of("abc")),
                Arguments.of("blank", " \n\t ", List.of()),
                Arguments.of("empty", "", List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    void splitsTextIntoWords(String scenario, String text, List<String> expected) throws IOException {
        assertThat(tokenize(text)).containsExactlyElementsOf(expected);
    }

    @Test
    void normalisesDecomposedAccentsToNfc() throws IOException {
        String decomposed = "Résumé";

        assertThat(tokenize(decomposed)).containsExactly("Résumé");
    }

    @Test
    void keepsSupplementaryCharactersWhole() throws IOException {
        String mathBold = "𝐇𝐞𝐥𝐥𝐨 world"; // letters outside the BMP, i.e. surrogate pairs

        assertThat(tokenize(mathBold)).containsExactly("𝐇𝐞𝐥𝐥𝐨", "world");
    }

    @Test
    void readBoundariesDoNotChangeTheResult() throws IOException {
        String text = "Ré́sumé 𝐇𝐞𝐥𝐥𝐨 don't state-of-the-art, end.";

        var oneCharAtATime = new ArrayList<String>();
        tokenizer.tokenize(new OneCharReader(text), oneCharAtATime::add);

        assertThat(oneCharAtATime).containsExactlyElementsOf(tokenize(text));
    }

    @Test
    void unpairedSurrogatesAreSeparators() throws IOException {
        assertThat(tokenize("ab\uD835cd")).containsExactly("ab", "cd");
        assertThat(tokenize("ab\uDC00cd")).containsExactly("ab", "cd");
        assertThat(tokenize("ab\uD835")).containsExactly("ab");
    }

    @Test
    void dropsWordsOverTheLengthLimitWithoutSplittingThem() throws IOException {
        var shortLimit = new WordTokenizer(5);
        var words = new ArrayList<String>();

        shortLimit.tokenize(new StringReader("short toolongword fine"), words::add);

        assertThat(words).containsExactly("short", "fine");
    }

    @Test
    void lengthLimitCountsJoiners() throws IOException {
        var shortLimit = new WordTokenizer(5);
        var words = new ArrayList<String>();

        shortLimit.tokenize(new StringReader("ab-cd abc-de"), words::add);

        assertThat(words).containsExactly("ab-cd");
    }

    @Test
    void lengthLimitAppliesAfterNormalisation() throws IOException {
        var limit = new WordTokenizer(4);
        String precomposed = "ÉÉÉÉ";
        String decomposed = "E\u0301E\u0301E\u0301E\u0301"; // 8 chars raw, 4 after NFC

        var words = new ArrayList<String>();
        limit.tokenize(new StringReader(precomposed + " " + decomposed), words::add);

        assertThat(words).containsExactly("ÉÉÉÉ", "ÉÉÉÉ");
    }

    @Test
    void stopsBufferingAWordFarBeyondTheLimit() throws IOException {
        var limit = new WordTokenizer(4);
        var words = new ArrayList<String>();

        limit.tokenize(new StringReader("a".repeat(1_000_000) + " ok"), words::add);

        assertThat(words).containsExactly("ok");
    }

    @Test
    void stopsWhenTheThreadIsInterrupted() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> tokenize("some words"))
                    .isInstanceOf(java.io.InterruptedIOException.class);
        } finally {
            Thread.interrupted(); // don't leak the flag into other tests
        }
    }

    @Test
    void rejectsLimitsOutOfRange() {
        assertThatThrownBy(() -> new WordTokenizer(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WordTokenizer(Integer.MAX_VALUE)).isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> tokenize(String text) throws IOException {
        var words = new ArrayList<String>();
        tokenizer.tokenize(new StringReader(text), words::add);
        return words;
    }

    /** Returns one char per read, which splits every surrogate pair across reads. */
    private static final class OneCharReader extends Reader {
        private final String text;
        private int position;

        OneCharReader(String text) {
            this.text = text;
        }

        @Override
        public int read(char[] buffer, int offset, int length) {
            if (position == text.length()) {
                return -1;
            }
            buffer[offset] = text.charAt(position++);
            return 1;
        }

        @Override
        public void close() {
        }
    }
}
