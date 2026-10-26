package io.github.bluething.searchindexer.document;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Path;
import java.util.Locale;

public final class DocumentTooLargeException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DocumentTooLargeException(Path file, long sizeBytes, long maxSizeBytes) {
        // Locale.ROOT: messages must not change with the machine's locale (e.g. "1.025" vs "1,025").
        super(String.format(Locale.ROOT, "%s is %,d bytes; the limit for this format is %,d bytes",
                file, sizeBytes, maxSizeBytes));
    }
}
