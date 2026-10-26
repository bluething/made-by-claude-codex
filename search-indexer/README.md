# search-indexer

A command-line indexer for a search system. For every file given on the command line it applies a set of
*indexing rules* and reports the result. Two rules ship today:

1. the number of words that start with an upper-case letter
2. the words that are longer than 5 characters

The codebase is meant to be read: it is small, but it shows how to build a production-grade CLI in modern
Java (streaming I/O, bounded concurrency, explicit failure modelling, a stable machine-readable output and an
extension point that is cheap to use). Design decisions are recorded in [`docs/adr`](docs/adr).

## Quick start

Requires **Java 25**. Maven is downloaded by the wrapper.

```bash
./mvnw verify                                   # compile, unit tests, package, jar integration tests, coverage gate

echo 'You are to create an Indexing process for a Search system.' > file1.txt
echo '<html><body><h1>Java Engineer</h1><p>production quality</p></body></html>' > file2.html
java -jar target/search-indexer.jar file1.txt file2.html
```

```text
file1.txt
  Words starting with an upper-case letter: 3
  Words longer than 5 characters: [create, Indexing, process, Search, system]
file2.html
  Words starting with an upper-case letter: 2
  Words longer than 5 characters: [Engineer, production, quality]
```

### Options

| Option                  | Default        | Meaning                                                  |
|-------------------------|----------------|----------------------------------------------------------|
| `-f`, `--format`        | `text`         | `text` for people, `jsonl` for pipelines (see below)     |
| `-p`, `--parallelism`   | number of CPUs | files indexed concurrently (1-1024); output order never changes |
| `-h`, `--help`          |                | usage, options and exit codes                            |
| `-V`, `--version`       |                | version from the jar manifest                            |

### Exit codes

| Code | Meaning                                                           |
|------|-------------------------------------------------------------------|
| 0    | every file was indexed                                            |
| 1    | at least one file could not be indexed; all others were           |
| 2    | invalid command line; nothing was indexed                         |
| 3    | run aborted: output could not be written, or interrupted          |

When several apply, the highest wins.

### JSON Lines output

`--format jsonl` writes one JSON object per file, in argument order, failures included, so a downstream
process can stream it. Field names are a stable contract.

```json
{"file":"a.txt","status":"indexed","results":{"uppercase-word-count":{"count":3},"long-words":{"terms":["Indexing"],"truncated":false}}}
{"file":"b.txt","status":"failed","error":{"reason":"not_found","message":"file not found","detail":"b.txt"}}
```

`reason` is one of `not_found`, `not_a_regular_file`, `access_denied`, `malformed_encoding`, `too_large`,
`io_error`, `internal_error`.

## Architecture

```
                      ┌──────────────────────────── indexing ─────────────────────────────┐
 cli.IndexCommand ──▶ │ BatchIndexer ──(N workers)──▶ DocumentIndexer                      │
   (picocli)          │   sliding window,             1. ContentExtractors → Reader         │
        ▲             │   input order                 2. WordTokenizer  → word, word, ...  │
        │             │        │                      3. every IndexingRule's accumulator   │
        │             └────────┼──────────────────────────────────────────────────────────┘
        │                      ▼ FileReport (Indexed | Failed)
        └──────────── report.ReportWriter (TextReportWriter | JsonLinesReportWriter)
```

| Package    | Responsibility                                                     | Depends on            |
|------------|--------------------------------------------------------------------|-----------------------|
| `text`     | `WordTokenizer`: streaming, Unicode-aware word splitting           | JDK only              |
| `rule`     | the rule API and the built-in rules                                | JDK only              |
| `document` | turning a file (plain text, HTML) into a `Reader` of text          | jsoup                 |
| `indexing` | one file → `FileReport`; many files in parallel, in order          | text, rule, document  |
| `report`   | presenting `FileReport`s as text or JSON Lines                     | indexing, Jackson     |
| `cli`      | command-line parsing and exit codes                                | everything, picocli   |
| `Main`     | composition root: the only class that wires concrete types         | everything            |

Dependencies point one way; `text` and `rule` (the domain) know nothing about files, formats or frameworks.

### How a file is processed

1. `DocumentIndexer` checks the path is a regular file, then asks `ContentExtractors` for the extractor:
   HTML by extension (`.html`, `.htm`, `.xhtml`), anything else as plain text.
2. The extractor returns a `Reader`. Plain text is streamed with a **strict** UTF-8 decoder; HTML is parsed with
   jsoup (bounded by a 64 MiB size limit, because it builds a DOM).
3. `WordTokenizer` reads the stream once and pushes every word to one `WordAccumulator` per rule. Memory is
   constant in the size of the file; only what a rule chooses to keep grows.
4. The accumulators' results become a `FileReport.Indexed`. Any I/O or decoding problem, at open time or half
   way through the file, becomes a `FileReport.Failed` with a `FailureReason`. A bug in a rule becomes
   `INTERNAL_ERROR` and is logged with its stack trace. One bad file never stops the batch.

## Extending

### Add a rule

```java
public final class NumberCountRule implements IndexingRule {

    @Override
    public String id() {
        return "number-count";                  // kebab-case, stable: it is a JSON key
    }

    @Override
    public String description() {
        return "Numbers";
    }

    @Override
    public WordAccumulator newAccumulator() {   // fresh state per file, used by one thread
        return new WordAccumulator() {
            private long count;

            @Override
            public void accept(String word) {
                if (word.chars().allMatch(Character::isDigit)) {
                    count++;
                }
            }

            @Override
            public RuleValue result() {
                return new RuleValue.Count(count);
            }
        };
    }
}
```

Then add it to `IndexingRules.defaults()` and write a unit test like `UppercaseWordCountRuleTest`. No other
code changes: both report formats already know how to render a `Count` or `Terms`.

If a rule needs a new kind of value (say, a word-frequency map), add a record to the sealed `RuleValue`.
The compiler then points at every report writer that has to render it.

### Add an input format

Implement `ContentExtractor` (return a `Reader` of the document's text) and register it in
`ContentExtractors.defaults()`. The first extractor whose `supports(Path)` returns true wins.

### Add an output format

Implement `ReportWriter` and add a constant to `ReportFormat`.

## Behaviour and assumptions

The brief leaves room for interpretation. These are the decisions, each pinned by a test:

| Topic | Decision |
|---|---|
| Word | Starts with a letter or digit; continues with letters, digits and combining marks. A single `'`, `’` or `-` *between* word characters joins (`don't`, `state-of-the-art`). Everything else separates. |
| Normalisation | Words are NFC-normalised, so `é` typed as one character or as `e` + accent is the same word with the same length. |
| Upper-case count | Every occurrence counts (`Java Java` = 2). Only the first character is checked (`iPhone` no, `NASA` yes), using Unicode's definition of upper case. Digits and scripts without case never count. |
| "Longer than 5" | Strictly more than 5 Unicode code points, so 6 or more. Inner `'`/`-` count. Numbers are words (`123456` qualifies). |
| Long-word list | Each distinct word once, in order of first appearance, original casing (`Search` and `search` are both listed). |
| HTML | Text content only: tags, attributes, comments, `<script>`, `<style>` ignored; `<title>` included; entities decoded. Text hidden with CSS still counts (knowing that would need a browser). |
| Encoding | UTF-8, BOM allowed. Invalid bytes fail the file (`malformed_encoding`) instead of being replaced with `�`, which would silently corrupt the index. |
| Errors | Reported per file; the rest of the batch continues. |

### Limits

Production input is not always friendly. Every unbounded thing has a bound, and hitting it is visible:

| Limit | Value | When exceeded |
|---|---|---|
| Word length | 255 UTF-16 units after NFC (Lucene's `StandardTokenizer` default) | the token is dropped; it is almost always encoded data, not a word. At most 4× that is buffered before normalisation, so a file with no separators can't exhaust memory |
| HTML file size | 64 MiB | file fails with `too_large` |
| Distinct long words per file | 100,000 | list is cut and marked `truncated` |
| Files in flight | 2 × parallelism | workers wait for the output to catch up |
| Parallelism | 1024 | usage error |

## Engineering practices in this repo

- **Build guards** (`pom.xml`): Maven Enforcer (Java 25+, Maven 3.9+, dependency convergence, every plugin
  version pinned), `javac -Xlint:all -Werror`, `--release 25`.
- **Tests**: unit tests per class, a parameterised tokenizer table, a reader that returns one char at a time to
  prove chunk boundaries don't matter, end-to-end CLI tests, and `PackagedJarIT`, which runs the real
  `java -jar` in a separate process.
- **Deterministic concurrency tests**: `BatchIndexerTest` forces each interleaving it claims to test with
  latches (never `sleep`), covering ordering, back-pressure, reader closing and cancellation on sink failure and
  on interruption. A class-level `@Timeout` turns a deadlock into a failure.
- **Coverage gate**: JaCoCo fails `verify` below 90% line / 80% branch coverage. The number is a tripwire
  for untested new code, not a goal; the tests above are chosen by risk, not to raise the percentage.
- **Reproducible tooling**: Maven wrapper pinned to 3.9.11. (A `distributionSha256Sum` is deliberately not set:
  wrapper 3.3.2's check calls `sha256sum -c` in a form that macOS's own `sha256sum` rejects.)

## Deliberately not done

| Not done | Why |
|---|---|
| DI framework (Spring, Guice) | One composition root (`Main`) wires seven objects; constructors are enough. |
| `ServiceLoader` rule discovery | An explicit list is easier to read and review. Worth it once rules ship in separate jars. |
| JPMS `module-info` | Friction with the shaded jar and picocli's reflection, for little gain in a CLI. |
| Structured concurrency | Still a preview API in Java 25. `BatchIndexer` would be a natural fit once it is final. |
| Positional/phrase rules | Rules see words, not positions. A richer token model should wait for a rule that needs it. |
| Content sniffing | Format is chosen by extension. Sniffing matters if files come from untrusted sources. |
