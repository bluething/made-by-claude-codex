# file-indexer

Command-line indexer for the "Java Software Dev Engineer Test". For each file given on the
command line it prints:

1. the number of words that start with an upper-case letter
2. the words that are longer than 5 characters

## Build and run

Requires Java 25 and Maven 3.9+.

```bash
mvn clean package
java -jar target/file-indexer.jar file1.txt file2.html
```

Example output:

```text
file1.txt
  Words starting with an upper-case letter: 3
  Words longer than 5 characters: [create, Indexing, process, Search, system]
file2.html
  Words starting with an upper-case letter: 2
  Words longer than 5 characters: [Engineer, production, quality]
```

Exit codes: `0` all files indexed, `1` at least one file failed (the others are still indexed,
errors go to stderr), `2` no arguments.

## How it works

```
Main ──> Indexer ──> DocumentReader  (file -> text; HTML -> text content via jsoup)
                ├──> Tokenizer       (text -> words, in document order)
                └──> IndexingRule*   (words -> RuleResult)
```

Nine small production files. The only runtime dependency is jsoup, because extracting text from
HTML with a regex is a bug factory (scripts, comments, entities, `<b>Hel</b>lo`).

## Adding a rule

1. Implement `IndexingRule`:

   ```java
   public final class NumberCountRule implements IndexingRule {
       public String name() { return "Numbers"; }
       public RuleResult apply(List<String> words) {
           return new RuleResult.Count(words.stream().filter(w -> w.chars().allMatch(Character::isDigit)).count());
       }
   }
   ```

2. Add it to `Rules.defaults()`.
3. Add a unit test next to the other rule tests.

If a rule needs a new kind of output (e.g. a word -> frequency map), implement
`RuleResult` with a new record.

## Assumptions

The brief is short, so these are the calls I made. All are covered by tests and each is a one-line
change if the team wants it differently.

- **What is a word**: a run of Unicode letters/digits (and accents); an apostrophe or hyphen *between*
  two such runs is kept (`don't`, `state-of-the-art` are one word). Punctuation around words is stripped.
  Text is NFC-normalised first, so `é` is the same whether it was typed precomposed or as `e` + accent.
- **Length** includes inner apostrophes/hyphens (`a-b-c-d` is 7 characters) and numbers are words
  (`123456` is longer than 5). Both are easy to change in `LongWordsRule` if the search team disagrees.
- **Upper-case count** counts every occurrence, not distinct words. `Java Java` = 2.
  Only the first character matters (`iPhone` no, `NASA` yes). Digits are not upper case.
- **"Longer than 5"** means length >= 6, measured in Unicode code points.
- **Long-word list** contains each word once, in order of first appearance, original casing kept.
  `Search` and `search` are listed separately: they are different tokens to a search index.
- **HTML** is detected by `.html`/`.htm` extension. Only the document's text content counts: tags,
  attributes, comments, `<script>` and `<style>` are ignored; `<title>` is included. Text hidden with CSS
  (`display:none`, `hidden`) is still counted, as working that out properly means rendering the page. Everything else is read as plain text.
- **Encoding**: files must be UTF-8. Invalid UTF-8 is reported as an error rather than silently
  producing garbage words.

## Trade-offs and what I'd do next

- **Files are read fully into memory** (text + token list, plus the DOM for HTML; roughly 5-10x the
  file size). Fine for documents up to a few hundred MB with default heap settings. For bigger inputs
  I'd change `IndexingRule` to a per-file accumulator (`accept(word)` / `result()`) and stream tokens
  from a reader; HTML would need a streaming parser too. Left until there's a real need.
- **Only words reach the rules.** Rules that need positions, sentences or HTML structure would need a
  richer input than `List<String>`; I didn't build that speculatively.
- **Sequential processing.** Files are independent, so parallelising is easy, but output ordering and
  error reporting get more complicated and the brief gives no performance target.
- **Plain-text output.** `RuleResult` is data, so a `--format=json` option for the downstream search
  system would be a new printer, not a rule change.
- **Rule configuration** (e.g. the 5-character threshold) is in code. If product wants to tune it
  without a release, move it to a CLI flag or config file.
- Content-type sniffing instead of file extensions, if the files come from untrusted sources.
- A process-level smoke test of the packaged jar (real exit code, manifest). I verified that by hand
  for this submission: `git clone` the bundle, `mvn package`, run the jar.
