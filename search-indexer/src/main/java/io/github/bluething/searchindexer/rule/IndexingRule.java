package io.github.bluething.searchindexer.rule;

/**
 * A business rule evaluated over the words of one document.
 *
 * <p>A rule is a stateless, thread-safe <em>factory</em>: the same instance is shared by every file being
 * indexed concurrently. Per-file state lives in the {@link WordAccumulator} it creates. This split lets the
 * indexer read each file once, feed every word to every rule, and never hold the whole document in memory.
 *
 * <h2>Scope</h2>
 * Rules see a stream of individual words. That covers counting, filtering and collecting terms. Rules that need
 * positions, sentence boundaries or document structure (phrases, proximity, headings) would need a richer input
 * than {@code String}; that is deliberately left until such a rule is actually required.
 *
 * <h2>Failure</h2>
 * If a rule throws, the whole file is reported as failed with an internal error; other files are unaffected.
 *
 * <h2>Adding a rule</h2>
 * <ol>
 *   <li>Implement this interface (see {@link UppercaseWordCountRule} for the smallest example).</li>
 *   <li>Register it in {@link IndexingRules#defaults()}.</li>
 *   <li>If none of the existing {@link RuleValue} shapes fit, add one; the compiler then lists every
 *       report writer that must learn to render it.</li>
 * </ol>
 */
public interface IndexingRule {

    /**
     * Stable, machine-readable identifier in kebab-case, e.g. {@code long-words}. Used as a key in JSON output,
     * so changing it is a breaking change for consumers. Must be unique among registered rules.
     */
    String id();

    /** Human-readable label, e.g. {@code Words longer than 5 characters}. */
    String description();

    /** Creates fresh state for one document. Called once per file. */
    WordAccumulator newAccumulator();
}
