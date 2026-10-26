package io.github.bluething.searchindexer.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlExtractorTest {

    @TempDir
    Path dir;

    private final HtmlExtractor extractor = new HtmlExtractor(StandardCharsets.UTF_8, 1024);

    @Test
    void supportsHtmlExtensionsCaseInsensitively() {
        assertThat(extractor.supports(Path.of("a.html"))).isTrue();
        assertThat(extractor.supports(Path.of("A.HTM"))).isTrue();
        assertThat(extractor.supports(Path.of("a.xhtml"))).isTrue();
        assertThat(extractor.supports(Path.of("a.txt"))).isFalse();
        assertThat(extractor.supports(Path.of("html"))).isFalse();
        assertThat(extractor.supports(Path.of(".html"))).isFalse();
    }

    @Test
    void keepsTextContentOnly() throws IOException {
        Path file = Files.writeString(dir.resolve("a.html"), """
                <html>
                  <head><title>Title</title><style>.Hidden{}</style></head>
                  <body class="Ignored">
                    <!-- Comment -->
                    <p>Hello <b>Wor</b>ld</p><p>Next</p>
                    <script>var Secret = 1;</script>
                  </body>
                </html>
                """);

        assertThat(readAll(file)).isEqualTo("Title Hello World Next");
    }

    @Test
    void decodesEntities() throws IOException {
        Path file = Files.writeString(dir.resolve("a.html"), "<p>caf&eacute; &amp; cr&egrave;me</p>");

        assertThat(readAll(file)).isEqualTo("café & crème");
    }

    @Test
    void rejectsFilesOverTheLimit() throws IOException {
        Path file = Files.writeString(dir.resolve("big.html"), "x".repeat(1025));

        assertThatThrownBy(() -> extractor.open(file))
                .isInstanceOf(DocumentTooLargeException.class)
                .hasMessageContaining("1,025 bytes");
    }

    @Test
    void acceptsFilesExactlyAtTheLimit() throws IOException {
        Path file = Files.writeString(dir.resolve("edge.html"), "x".repeat(1024));

        assertThat(readAll(file)).hasSize(1024);
    }

    @Test
    void rejectsInvalidUtf8() throws IOException {
        Path file = Files.write(dir.resolve("bad.html"), new byte[] {'<', 'p', '>', (byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> extractor.open(file)).isInstanceOf(CharacterCodingException.class);
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new HtmlExtractor(StandardCharsets.UTF_8, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HtmlExtractor(StandardCharsets.UTF_8, Long.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String readAll(Path file) throws IOException {
        try (Reader reader = extractor.open(file)) {
            var text = new StringWriter();
            reader.transferTo(text);
            return text.toString();
        }
    }
}
