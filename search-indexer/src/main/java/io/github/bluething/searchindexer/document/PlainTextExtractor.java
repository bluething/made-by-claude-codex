package io.github.bluething.searchindexer.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads any file as text, streaming. Used as the fallback for formats no other extractor claims. */
public final class PlainTextExtractor implements ContentExtractor {

    private final Charset charset;

    public PlainTextExtractor(Charset charset) {
        this.charset = Objects.requireNonNull(charset, "charset");
    }

    @Override
    public boolean supports(Path file) {
        return true;
    }

    @Override
    public Reader open(Path file) throws IOException {
        InputStream in = Files.newInputStream(file);
        try {
            return TextDecoding.strictReader(in, charset);
        } catch (IOException | RuntimeException e) {
            // Nobody else holds the stream yet, so close it here, without letting a close
            // failure hide the original problem.
            try {
                in.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }
}
