package io.github.bluething.indexer.rule;

import java.util.List;

/**
 * One business rule applied to the words of a single file.
 * <p>
 * To add a rule: implement this interface and register it in {@link Rules#defaults()}.
 */
public interface IndexingRule {

    /** Human-readable label used in the output, e.g. "Words longer than 5 characters". */
    String name();

    /**
     * @param words the file's words in document order, duplicates included
     */
    RuleResult apply(List<String> words);
}
