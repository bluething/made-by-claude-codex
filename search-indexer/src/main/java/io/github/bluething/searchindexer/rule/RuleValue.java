package io.github.bluething.searchindexer.rule;

import java.util.List;
import java.util.Objects;

/**
 * The value a rule produces.
 *
 * <p>Sealed on purpose: report writers {@code switch} over it exhaustively, with no {@code default} branch.
 * A new rule that reuses an existing shape needs no writer changes; a new shape fails compilation in every
 * writer until it is handled, instead of printing {@code toString()} at runtime.
 */
public sealed interface RuleValue {

    record Count(long value) implements RuleValue {
        public Count {
            if (value < 0) {
                throw new IllegalArgumentException("count must not be negative, was " + value);
            }
        }
    }

    /**
     * @param truncated {@code true} if the rule stopped collecting at a limit, so {@code values} is a prefix of the
     *                  full answer. Reported explicitly rather than silently returning partial data.
     */
    record Terms(List<String> values, boolean truncated) implements RuleValue {
        public Terms {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }
}
