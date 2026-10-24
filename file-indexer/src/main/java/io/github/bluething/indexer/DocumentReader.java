package io.github.bluething.indexer;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Loads a file and returns the text that rules should see.
 * HTML files are reduced to their visible text; everything else is treated as plain UTF-8 text.
 */
public final class DocumentReader {

    private DocumentReader() {
    }

    public static String read(Path file) throws IOException {
        // readString fails fast on malformed UTF-8 instead of silently producing garbage words
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return isHtml(file) ? Jsoup.parse(content).text() : content;
    }

    private static boolean isHtml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }
}
