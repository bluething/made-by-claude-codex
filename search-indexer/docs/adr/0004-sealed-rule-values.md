# 4. Sealed result types, exhaustive writers

## Context

Rules produce different shapes of result (a count, a list of terms), and each output format must render all
of them. With an open interface and a `render()` method, every rule would have to know every output format;
with `Object value` and `instanceof` chains, a new shape silently falls into a default branch.

## Decision

`RuleValue` is a `sealed interface` with record implementations (`Count`, `Terms`). Writers use a
`switch` with pattern matching and **no `default` branch**. `FileReport` follows the same pattern.

## Consequences

- A new rule that reuses an existing shape needs no writer changes. This is the common case.
- A new shape is a compile error in every writer until it is handled: the compiler is the checklist.
- Rules stay unaware of output formats; writers stay unaware of individual rules.
- Adding a shape is a coordinated change in this codebase. If third parties ever ship rules in separate
  jars, this decision should be revisited.
