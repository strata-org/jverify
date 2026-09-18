# bytecode-frontend

A prototype **bytecode-based** Strata frontend, hosted inside the
StrataJavaFrontEnd (JFE) package as sibling Gradle modules. Where JFE compiles
Java *source* to Laurel (Strata's IR) through javac internals, this frontend
compiles **JVM classfiles**:

```
.java --javac -g -Xjcov--> .class --java.lang.classfile--> CFG
    --structurer--> JavaParser AST (+ CharacterRangeTable spans)
    --Laurel generator--> Laurel --Ion--> strata laurelAnalyzeBinary --> diagnostics
```

Input classfiles must be compiled with `-g -Xjcov` so local-variable names,
line numbers, and statement-level source ranges (CharacterRangeTable) survive.

## Modules

- **`bytecode-frontend`** -- the decompiler (`org.strata.java.bytecode.frontend`), the
  Laurel generator and driver (`org.strata.java.bytecode.verifier`), and the test
  engine (`org.strata.java.bytecode.testengine`).
- **The CLASS-retention spec library** is built by isolated tasks in this
  module's `build.gradle.kts` block (`generateBytecodeLibSources` ->
  `compileBytecodeLib` -> `bytecodeLibJar`), not as a separate Gradle module.
  It has no hand-written sources: they are *generated* from `:library`. It is
  deliberately **not** a module -- see the retention note below.

## Relationship to JFE (one-way, JFE is unaware)

The dependency arrow points only from `bytecode-*` to JFE modules; no JFE module,
source file, or test references the bytecode frontend. Concretely:

- **Laurel AST + Ion serialization + driver support** (`org.strata.jverify.laurel.*`,
  `JVerifyResults`, `SourceFile`, `IntervalTree`, `FilesMap`, `DiagnosticWithRange`,
  ...) are **reused from `:verifier`**, not copied -- so the Laurel schema can
  never drift out of sync.
- **`@JVerifyTest`, `TestMarkup`, `Blocker`, `JVerifyTestRecord`** are reused
  from `:test-engine`. Only the engine that *drives the bytecode pipeline*
  (`BytecodeTestEngine`) and the xfail manifest are bytecode-frontend-owned.
- **The spec library** is the one place JFE and the bytecode frontend genuinely differ:
  annotation retention must be `CLASS` (not JFE's `SOURCE`) so `@Pure`/`@Nat`/
  `@Unbounded`/`@Contract` survive into classfiles where the decompiler reads
  them. Rather than maintain a second copy, the `generateBytecodeLibSources`
  task copies `:library`'s sources at build time, rewriting only the retention
  token, and `compileBytecodeLib`/`bytecodeLibJar` compile them into a plain
  jar used *only* as the driver's `javac -g -Xjcov` classpath (the artifacts
  land under `build/bytecode-lib/` -- generated sources, classes, and
  `bytecode-frontend-lib.jar`; nothing is checked in). It is deliberately
  **not** a Gradle module: it shares package `org.strata.jverify` with
  `:library`, and Brazil puts every module jar on every other module's compile
  classpath, so a second module exporting that package would split it across
  two JPMS modules and break JFE's own `module-info`'d builds (e.g.
  `contracts2jqwik`). Flip `:library` to `CLASS` someday and this whole seam
  collapses to a plain dependency.

### How isolation holds under Brazil

Brazil flattens every module's output onto a shared test runtime classpath, so
`BytecodeTestEngine` (registered via `@AutoService`) is *visible* to JFE's own test
tasks. It stays inert unless activated by the `bytecode.corpus.root` system
property, which only `corpusTest` sets -- and it is passed as a real `-D` launch
argument (not `Test.systemProperty`) so it cannot leak into a reused Gradle
worker and spuriously activate the engine under, e.g., `:examples:test`.

## Tests

- `./... :bytecode-frontend:test` -- the bytecode frontend's own unit tests (decompiler, driver,
  backend smoke), plain JUnit.
- `./... :bytecode-frontend:corpusTest` -- runs **JFE's `@JVerifyTest` verification
  corpus** (compiled `:verifier` test classes) through the bytecode frontend, a
  single source of truth shared with JFE. Bytecode-specific expected failures
  live in
  [`bytecode-xfails.txt`](src/main/resources/org/strata/java/bytecode/testengine/bytecode-xfails.txt),
  never as edits to JFE's fixtures. Wired into `check`.

See [docs/parity-matrix.md](docs/parity-matrix.md) for the current pass/skip
standing and the remaining gaps versus JFE. Build with `brazil-build` (it puts
`strata`/`z3`/`cvc5` on `PATH`); a plain `gradlew` run cannot launch the Strata
backend.
