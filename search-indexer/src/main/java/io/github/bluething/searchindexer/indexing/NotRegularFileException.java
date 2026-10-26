package io.github.bluething.searchindexer.indexing;

import java.io.Serial;
import java.nio.file.FileSystemException;
import java.nio.file.Path;

final class NotRegularFileException extends FileSystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    NotRegularFileException(Path file) {
        super(file.toString());
    }
}
