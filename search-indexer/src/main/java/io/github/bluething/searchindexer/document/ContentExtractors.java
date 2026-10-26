package io.github.bluething.searchindexer.document;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Chooses the {@link ContentExtractor} for a file: the first registered one that supports it, otherwise the
 * fallback. To support a new format, implement {@code ContentExtractor} and add it in {@link #defaults()}.
 */
public final class ContentExtractors {

    /** HTML is parsed into a DOM, which needs several times the file size in heap. */
    public static final long DEFAULT_MAX_HTML_BYTES = 64L * 1024 * 1024;

    private final List<ContentExtractor> extractors;
    private final ContentExtractor fallback;

    public ContentExtractors(List<ContentExtractor> extractors, ContentExtractor fallback) {
        this.extractors = List.copyOf(extractors);
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /** UTF-8 everywhere; HTML by extension; anything else as plain text. */
    public static ContentExtractors defaults() {
        return new ContentExtractors(
                List.of(new HtmlExtractor(StandardCharsets.UTF_8, DEFAULT_MAX_HTML_BYTES)),
                new PlainTextExtractor(StandardCharsets.UTF_8));
    }

    public ContentExtractor forFile(Path file) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(file))
                .findFirst()
                .orElse(fallback);
    }
}
