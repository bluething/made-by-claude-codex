package io.github.bluething.searchindexer.cli;

/**
 * Process exit codes. When several conditions apply, the highest wins: an aborted run (3) that also had
 * failed files exits with 3.
 */
public enum ExitCode {
    /** Every file was indexed. */
    SUCCESS(0),
    /** At least one file could not be indexed; all others were. */
    FILES_FAILED(1),
    /** Invalid command line; nothing was indexed. */
    USAGE(2),
    /** The run stopped early: output could not be written, the process was interrupted, or a bug escaped. */
    ABORTED(3);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
