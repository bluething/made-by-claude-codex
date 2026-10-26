# 1. No application framework, manual wiring

Status: accepted

## Context

The application is a CLI with about a dozen long-lived objects. It has to start fast, every
run starts empty, and it runs wherever `start.sh` is executed, with only a JDK guaranteed.

## Decision

No Spring, Micronaut, Guice or Picocli. Each class gets its dependencies through its
constructor, and `AtmApplication.main` creates and connects everything in about ten lines.
The only dependencies are for tests: JUnit, AssertJ and ArchUnit.

## Consequences

- Start-up is instant, the jar has no runtime dependencies, and there is no hidden behaviour.
- All the wiring fits on one screen, and the classes are just as easy to construct in tests.
- If the application grows (HTTP API, configuration profiles, many more components), a DI
  container would start to pay off. Because everything already uses constructor injection,
  adopting one would be mechanical.
