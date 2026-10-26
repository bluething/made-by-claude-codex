package io.github.bluething.searchindexer.document;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class FileExtensions {

    private FileExtensions() {
    }

    /** Case-insensitive match of the file name's extension, given without the dot. */
    static boolean hasAny(Path file, Set<String> extensions) {
        Path name = file.getFileName();
        if (name == null) {
            return false;
        }
        String fileName = name.toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && extensions.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
