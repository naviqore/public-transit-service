# Agent Instructions

Guidelines for AI coding agents (and humans) working on this repository. See [CONTRIBUTING.md](CONTRIBUTING.md) for
the contribution process and commit conventions.

## Project

Java 25, multi-module Maven build (use the wrapper `./mvnw`):

- `libs/utils`, `libs/timetable`, `libs/gtfs`, `libs/gtfs-sample`, `libs/raptor`, `libs/public-transit-service`: the
  libraries, from shared utilities to the routing service.
- `app`: the Spring Boot REST application.
- `benchmark`: benchmarks of the routing service.

## Before Finishing a Task

1. Run `./mvnw clean verify`. It must pass: the compiler runs with `-Xlint:all`, Error Prone and NullAway, and fails
   on any warning, including deprecations.
2. If the SonarQube MCP server or SonarQube for IDE is available, analyze the changed files and fix the reported issues.
   SonarQube Cloud analyzes every pull request in CI.

Fix the cause of a warning rather than suppressing it. Suppress only deliberate cases, as narrowly as possible, and
comment why.

## Code Conventions

- Follow the IntelliJ code style in `.idea/codeStyles` (lines up to 120 characters) and keep the shared inspection
  profile in `.idea/inspectionProfiles` free of warnings.
- Lombok is used for boilerplate, e.g. getters, constructors and builders.
- Null safety: every main package is `@NullMarked` (JSpecify) in its `package-info.java`, so types are non-null unless
  annotated `@Nullable`. Add a `package-info.java` to new main packages. For arrays, `@Nullable X[]` means nullable
  elements, `X @Nullable []` a nullable array.
- Use `Objects.requireNonNull` for lookups whose keys are known to be present, and `assert` for internal invariants.
- Records may hold arrays. Builder methods may return values callers ignore, and classes need not be records.
- Comments explain why, not what, and stay short.

## Tests

- JUnit 5. Unit tests run with Surefire; integration tests are named `*IT` and run with Failsafe.
- Add tests for changed behavior, and keep them focused.
