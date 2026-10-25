package io.github.bluething.indexer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenizerTest {

    @Test
    void splitsOnWhitespaceAndPunctuation() {
        assertThat(Tokenizer.words("Hello, world! How are you?"))
                .containsExactly("Hello", "world", "How", "are", "you");
    }

    @Test
    void keepsDuplicatesInDocumentOrder() {
        assertThat(Tokenizer.words("the cat and the hat"))
                .containsExactly("the", "cat", "and", "the", "hat");
    }

    @Test
    void keepsInnerApostrophesAndHyphens() {
        assertThat(Tokenizer.words("don't over-engineer it's state-of-the-art"))
                .containsExactly("don't", "over-engineer", "it's", "state-of-the-art");
    }

    @Test
    void dropsLeadingAndTrailingApostrophesAndHyphens() {
        assertThat(Tokenizer.words("'quoted' -dash- -- ''"))
                .containsExactly("quoted", "dash");
    }

    @Test
    void handlesUnicodeLetters() {
        assertThat(Tokenizer.words("Ångström café naïve Straße"))
                .containsExactly("Ångström", "café", "naïve", "Straße");
    }

    @Test
    void decomposedAccentsAreNormalisedIntoTheSameWord() {
        String decomposed = "Re\u0301sume\u0301"; // e + combining acute accent

        assertThat(Tokenizer.words(decomposed)).containsExactly("Résumé");
    }

    @Test
    void returnsEmptyListForBlankText() {
        assertThat(Tokenizer.words("  \n\t ")).isEmpty();
        assertThat(Tokenizer.words("")).isEmpty();
    }
}
