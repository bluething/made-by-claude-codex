package io.github.bluething.searchindexer.document;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts the text content of an HTML document: tags, attributes, comments, {@code <script>} and
 * {@code <style>} are dropped; {@code <title>} is kept. Block elements are separated by whitespace, inline
 * elements are not ({@code Hel<b>lo</b>} is one word).
 *
 * <p>Text hidden with CSS is still included: deciding visibility means rendering the page.
 *
 * <p>Unlike plain text this is not streamed, because jsoup builds a DOM. Peak memory is roughly the raw bytes,
 * plus the decoded string, plus the DOM, plus the extracted text: budget about 10x the file size.
 * {@code maxSizeBytes} keeps one oversized file from exhausting the heap; such files fail with
 * {@link DocumentTooLargeException}.
 */
public final class HtmlExtractor implements ContentExtractor {

    private static final Set<String> EXTENSIONS = Set.of("html", "htm", "xhtml");

    /** The whole file becomes one byte array, and arrays stop a little short of Integer.MAX_VALUE. */
    private static final long MAX_SUPPORTED_BYTES = Integer.MAX_VALUE - 16;

    private final Charset charset;
    private final long maxSizeBytes;

    public HtmlExtractor(Charset charset, long maxSizeBytes) {
        if (maxSizeBytes < 1 || maxSizeBytes > MAX_SUPPORTED_BYTES) {
            throw new IllegalArgumentException(
                    "maxSizeBytes must be in [1, " + MAX_SUPPORTED_BYTES + "], was " + maxSizeBytes);
        }
        this.charset = Objects.requireNonNull(charset, "charset");
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    public boolean supports(Path file) {
        return FileExtensions.hasAny(file, EXTENSIONS);
    }

    @Override
    public Reader open(Path file) throws IOException {
        // Fail fast on the common case without reading anything.
        long size = Files.size(file);
        if (size > maxSizeBytes) {
            throw new DocumentTooLargeException(file, size, maxSizeBytes);
        }
        String html = TextDecoding.strictDecode(readAtMost(file), charset);
        return new StringReader(Jsoup.parse(html).text());
    }

    /** Enforces the limit while reading too: the file may have grown since {@link Files#size} was called. */
    private byte[] readAtMost(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes((int) maxSizeBytes + 1);
            if (bytes.length > maxSizeBytes) {
                throw new DocumentTooLargeException(file, bytes.length, maxSizeBytes);
            }
            return bytes;
        }
    }
}
