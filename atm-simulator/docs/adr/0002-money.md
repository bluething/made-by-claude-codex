# 2. Money as a normalised BigDecimal

Status: accepted

## Context

The examples use whole dollars, but a bank deals in cents. `double` can't represent
`0.10` exactly. A `long` counting cents is exact and fast, but every addition then needs
an overflow check, and an overflow in the middle of a multi-step repayment has to be
handled carefully.

## Decision

`Money` is a record that wraps a `BigDecimal`:
- it is always normalised to scale 2, so `5`, `5.0` and `5.00` are `equals` and have the
  same `hashCode`;
- it is never negative and never rounded: an amount that has non-zero digits below one
  cent (`1.005`) is rejected instead of losing money. Trailing zeros are harmless, so
  `Money.of("1.500")` is simply `1.50`;
- customer input is checked against a strict format (`\d{1,12}(\.\d{1,2})?`) before it
  becomes `Money`, so signs, exponents (`1e3`) and grouping (`1,000`) are rejected at the
  edge.

Formatting is a presentation concern: `ConsolePresenter` prints whole dollars as `$100`,
to match the spec, and anything else as `$100.50`.

## Consequences

- Arithmetic is exact and can't overflow, so a whole class of bugs can't happen.
- `BigDecimal` is slower than `long`, which doesn't matter at ATM speed.
- A single currency is assumed. Supporting several would mean adding a `Currency` field
  to `Money` and refusing to add amounts in different currencies.
- `long` minor units is an equally valid choice (it was discussed in design review).
  Callers wouldn't notice the switch because `Money` hides its representation.
