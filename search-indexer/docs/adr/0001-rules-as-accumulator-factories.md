# 1. Rules are factories of per-file accumulators

## Context

The brief expects "many more indexing rules" and input files may be large. The simplest rule API,
`RuleResult apply(List<String> words)`, needs every word of a file in memory at once and makes each rule
iterate the list again.

## Decision

```java
interface IndexingRule     { String id(); String description(); WordAccumulator newAccumulator(); }
interface WordAccumulator  { void accept(String word); RuleValue result(); }
```

A rule is stateless and shared by all threads. For each file the indexer asks every rule for a fresh
accumulator, tokenizes the file **once**, and pushes each word to all accumulators. This is the same shape as
`java.util.stream.Collector` (supplier / accumulator / finisher), without the combiner we don't need.

## Consequences

- Memory per file is constant apart from what a rule deliberately keeps (e.g. the long-word set, which is
  capped).
- Adding a rule never touches the tokenizer, the file handling or other rules.
- Thread-safety is structural: shared objects are immutable, mutable state is confined to one thread.
- A rule only sees words. Rules that need positions or document structure need a richer input; we will add
  that when such a rule exists rather than guess its shape now.
