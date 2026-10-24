package io.github.bluething.indexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentReaderTest {

    @TempDir
    Path dir;

    @Test
    void readsPlainTextAsIs() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "<b>not html</b>");

        assertThat(DocumentReader.read(file)).isEqualTo("<b>not html</b>");
    }

    @Test
    void htmlKeepsOnlyVisibleText() throws IOException {
        Path file = Files.writeString(dir.resolve("a.html"), """
                <html>
                  <head><title>Title</title><style>.Hidden{}</style></head>
                  <body class="Ignored">
                    <p>Hello <b>World</b></p>
                    <script>var Secret = 1;</script>
                  </body>
                </html>
                """);

        assertThat(DocumentReader.read(file))
                .contains("Title", "Hello World")
                .doesNotContain("Hidden", "Secret", "Ignored", "<p>");
    }

    @Test
    void htmlExtensionIsCaseInsensitive() throws IOException {
        Path file = Files.writeString(dir.resolve("PAGE.HTM"), "<p>Hi</p>");

        assertThat(DocumentReader.read(file)).isEqualTo("Hi");
    }

    @Test
    void rejectsInvalidUtf8() throws IOException {
        Path file = Files.write(dir.resolve("bin.txt"), new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> DocumentReader.read(file)).isInstanceOf(MalformedInputException.class);
    }
}
