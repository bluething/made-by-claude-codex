package io.github.bluething.searchindexer.indexing;

import io.github.bluething.searchindexer.document.DocumentTooLargeException;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;

/** Why a file could not be indexed. Stable names: they appear in JSON output. */
public enum FailureReason {
    NOT_FOUND("file not found"),
    NOT_A_REGULAR_FILE("not a regular file"),
    ACCESS_DENIED("permission denied"),
    MALFORMED_ENCODING("not valid text in the expected encoding"),
    TOO_LARGE("file too large"),
    IO_ERROR("I/O error"),
    /** A defect in this program (most likely a rule), not a problem with the input. */
    INTERNAL_ERROR("internal error");

    private final String description;

    FailureReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    static FailureReason of(IOException e) {
        return switch (e) {
            case NoSuchFileException _, NotDirectoryException _ -> NOT_FOUND;
            case AccessDeniedException _ -> ACCESS_DENIED;
            case NotRegularFileException _ -> NOT_A_REGULAR_FILE;
            case CharacterCodingException _ -> MALFORMED_ENCODING;
            case DocumentTooLargeException _ -> TOO_LARGE;
            default -> IO_ERROR;
        };
    }
}
