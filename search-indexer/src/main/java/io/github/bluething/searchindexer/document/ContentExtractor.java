package io.github.bluething.searchindexer.document;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

/**
 * Turns a file of some format into the plain text that rules should see.
 *
 * <p>Implementations must be thread-safe: one instance is shared by all worker threads. Keep per-file state
 * (decoders, parsers) local to {@link #open} or inside the returned reader.
 *
 * <p>Implementations should stream where the format allows it. Decoding errors may surface either from
 * {@link #open} or later while the returned reader is consumed; callers must handle both.
 */
public interface ContentExtractor {

    /** Whether this extractor handles {@code file}. Must be cheap: called before the file is opened. */
    boolean supports(Path file);

    /** Opens {@code file} as text. The caller closes the returned reader. */
    Reader open(Path file) throws IOException;
}
