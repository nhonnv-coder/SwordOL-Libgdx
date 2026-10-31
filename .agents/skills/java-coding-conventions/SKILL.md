---
name: java-coding-conventions
description: Use when writing, formatting, refactoring, or reviewing Java source code, or defining Java coding conventions in Maven, Gradle, Android, server, desktop, and library projects.
---

# Java coding conventions

Apply consistent Java conventions while preserving the project's Java version, established style, public contracts, and runtime behavior. This is a reusable project-independent reference, not a mandate to migrate a codebase.

## Determine the effective convention

Before editing, inspect applicable repository instructions, build files, formatter/linter configuration, and representative neighboring Java files.

Use this precedence: explicit user requirements, applicable repository instructions, enforced formatter/linter settings, consistent local conventions, then the defaults below. Report a concrete conflict instead of silently changing enforcement. Distinguish formatting from correctness or design suggestions during review.

Determine the source/API target from Maven compiler release/source/target, Gradle toolchains/options, or Android settings. The installed JDK alone does not establish compatibility. Use only language features and APIs supported by that target and its runtime; check Android API/desugaring constraints separately. Do not introduce records, var, text blocks, newer collection factories, or virtual threads into older targets.

## Formatting defaults

These are this skill's fallback choices, not a claim that Java has one universal style. Existing project settings take precedence.

| Item | Default when the project has no convention |
| --- | --- |
| Encoding | UTF-8; final newline; no trailing whitespace |
| Indentation | 4 spaces, no tabs; continuation indent 8 spaces |
| Width | Target 120 columns; wrap at syntactic boundaries |
| Braces | Opening brace on the declaration/control line; braces on every control body |
| Spacing | Space around binary operators and after commas; `if (` and `for (`; `method(` |
| Statements | One statement per line; expand compressed control flow |
| Imports | Explicit imports, no wildcards or unused imports; static group then non-static group, each sorted, separated by a blank line |
| Layout | Package, imports, type; one public top-level type per matching filename |
| Whitespace | One blank line between methods or logical sections; avoid manual column alignment |

Let the configured formatter decide wrapping and import layout. Preserve existing line endings in focused changes. Do not reformat unrelated files or generated/vendor code. For an explicitly requested repository-wide format, establish the formatter configuration first and keep mechanical changes separate from behavior changes when practical.

## Naming and type organization

- Packages: lowercase, meaningful namespace; follow the project's established hierarchy.
- Classes, interfaces, enums, annotations, and records: `UpperCamelCase`; prefer domain names over vague `Manager` or `Helper` names.
- Methods, fields, parameters, and locals: `lowerCamelCase`; methods express actions, booleans express predicates such as `isReady` or `hasItems`.
- Constants: `UPPER_SNAKE_CASE` for actual constants. A mutable object does not become a constant merely because its reference is `static final`.
- Type parameters: concise conventional names (`T`, `K`, `V`) or descriptive names when several roles would be ambiguous.
- Avoid unexplained abbreviations, redundant type prefixes, and inconsistent acronym capitalization. Preserve names imposed by external APIs or serialization contracts.
- Keep fields private by default, use the narrowest visibility, and mark references final when reassignment is unnecessary. Final references do not imply deep immutability or thread safety.
- Group related members coherently; use a consistent order such as constants, fields, constructors, public operations, and private helpers. Do not reorder existing members solely for a style cleanup.
- Prefer cohesive methods and composition. Extract a method when it clarifies a responsibility; do not impose arbitrary line-count limits or introduce layers solely to satisfy a convention.

## Correctness conventions

- State and validate nullability at boundaries using the project's annotations/contracts. Use `Objects.equals` for nullable value comparison; use `==` for primitives, enums, or intentional identity checks. Do not silently replace an existing null contract with Optional.
- Keep `equals` and `hashCode` consistent. Avoid mutable map keys and accidental array identity comparisons. Check collection mutability requirements before returning defensive copies or immutable views.
- Use generics rather than raw types or unchecked casts. Program to an interface when callers do not need implementation-specific behavior.
- Choose loops or streams for readability. Avoid side effects in stream pipelines and do not introduce parallel streams without a demonstrated execution and performance reason.
- Use precise numeric types: `BigDecimal` or an established minor-unit representation for exact monetary arithmetic; avoid constructing decimal money from binary floating-point values.
- Use explicit character encodings for byte/text boundaries. Prefer `java.time` where supported, keeping timezone and clock assumptions explicit.
- Preserve public signatures, persistence mappings, wire formats, reflection entry points, and framework-required constructors or annotations. A rename or field visibility change can affect behavior even if compilation succeeds.

## Exceptions, resources, logging, and threads

- Catch specific exceptions at the layer that can recover or translate them. Preserve causes and actionable context; avoid empty catches, misleading default success values, and catching `Throwable` for ordinary recovery.
- Use try-with-resources for resources owned by the current operation. Do not close caller-owned streams or connections without an ownership contract.
- When handling `InterruptedException` without propagating it, restore interrupt status and return, stop, or otherwise honor cancellation. Do not immediately retry blocking work forever.
- Use the project's logging framework and its supported parameter syntax. Log a failure at the responsible boundary; avoid repeated log-and-rethrow noise. Exclude credentials, access tokens, and sensitive payloads.
- Make shared-state ownership explicit. Use immutable data, confinement, locks, atomics, or concurrent collections according to the invariant. `volatile` does not make compound operations atomic.
- Respect executor/task lifecycle and UI/event-loop thread rules. Do not add blocking work to a render thread or request event loop. Use newer concurrency APIs only when supported by the target runtime.

## Documentation and tests

- Explain contracts, ownership, units, nullability, thread requirements, and surprising decisions. Avoid comments that merely translate the code into prose.
- Add useful Javadoc for public APIs where the project expects it; document meaningful failure conditions and side effects. Preserve required license headers.
- Follow the existing test framework and naming style. For behavior changes, test observable outcomes, boundaries, and relevant failure paths. Use stable synchronization or controllable clocks rather than sleep-based timing.
- A formatting-only change normally needs formatter verification and the project's relevant compile/check commands, not new tests that assert whitespace.

## Example: fallback style and owned resources

This Java 8-compatible example owns the reader it opens, uses explicit UTF-8, and leaves error translation to its caller. It reads the entire file and is intended for files known to fit in memory.

```java
package example.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LineReader {
    private LineReader() {
    }

    public static List<String> readNonEmptyLines(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }
}
```

## Apply and verify

1. Identify the target version and effective style before proposing edits. For a convention-writing request, explicitly label newly chosen defaults.
2. Change only the requested scope. Separate behavior fixes from mechanical formatting; offer unrelated findings separately.
3. Use the repository's wrapper, configured formatter/check tasks, and relevant tests. Inspect build configuration before choosing commands: a Spotless or Checkstyle task is not necessarily installed, and Checkstyle is not a formatter.
4. When adding enforcement is requested, reuse the existing tool where possible. Otherwise select a compatible tool and pin its version/configuration. Do not add dependencies or upgrade Java merely to format code. A formatter's output becomes the effective layout rather than a competing hand-written layout.
5. Inspect the resulting diff for accidental API changes, generated files, unrelated formatting churn, and secrets in logs.
6. Report the chosen convention, changed scope, verification actually run, and any unverified constraints. Do not claim formatting proves semantic correctness or thread safety.

Common mistakes: imposing fallback indentation over repository settings; using modern APIs on an older target; renaming serialized fields as a style change; closing borrowed resources; assuming final means immutable; and claiming a formatter/test passed without running it.
