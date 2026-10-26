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

class PlainTextExtractorTest {

    @TempDir
    Path dir;

    private final PlainTextExtractor extractor = new PlainTextExtractor(StandardCharsets.UTF_8);

    @Test
    void supportsAnyFile() {
        assertThat(extractor.supports(Path.of("anything.bin"))).isTrue();
    }

    @Test
    void readsTextUnchanged() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "<b>not html</b> café");

        assertThat(readAll(file)).isEqualTo("<b>not html</b> café");
    }

    @Test
    void stripsUtf8ByteOrderMark() throws IOException {
        Path file = Files.write(dir.resolve("bom.txt"), new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'i'});

        assertThat(readAll(file)).isEqualTo("Hi");
    }

    @Test
    void reportsMalformedBytesInsteadOfReplacingThem() throws IOException {
        // Valid text first, so the error surfaces while reading, not when opening.
        byte[] content = ("x".repeat(20_000) + "ÿ").getBytes(StandardCharsets.ISO_8859_1);
        Path file = Files.write(dir.resolve("latin1.txt"), content);

        assertThatThrownBy(() -> readAll(file)).isInstanceOf(CharacterCodingException.class);
    }

    private String readAll(Path file) throws IOException {
        try (Reader reader = extractor.open(file)) {
            var text = new StringWriter();
            reader.transferTo(text);
            return text.toString();
        }
    }
}
