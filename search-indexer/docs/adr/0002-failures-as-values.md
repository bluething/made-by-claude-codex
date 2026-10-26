# 2. A failed file is a value, not an exception

## Context

A batch run over many files will meet missing files, directories, permission problems, non-UTF-8 content and
oversized HTML. Users want every good file indexed and a clear list of the bad ones. Some failures (decoding)
only appear half way through reading a stream.

## Decision

`DocumentIndexer.index(Path)` never throws for a bad file. It returns the sealed type
`FileReport = Indexed | Failed`, where `Failed` carries a stable `FailureReason` enum and a detail message.

- `IOException`s (from opening *or* reading) are classified into a `FailureReason` in one place.
- A `RuntimeException` is a bug, most likely in a rule: it becomes `INTERNAL_ERROR` and is logged with its
  stack trace, so the batch survives but the defect is not hidden.
- `Error`s (e.g. `OutOfMemoryError`) are not caught.
- Partial rule results from a file that failed half way are discarded, never reported.

Errors writing the *output* are different: they are not about one file, so they abort the run (exit code 3).

## Consequences

- Report writers decide how failures look (stderr for text, an inline record for JSON Lines).
- `FailureReason` names are part of the JSON contract and must not be renamed casually.
- Exit code 1 ("some files failed") is computed from the reports, not from caught exceptions.
