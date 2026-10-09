# bytecode-frontend

StrataJavaFrontEnd's Strata frontend. It translates **JVM classfiles** to
Laurel (Strata's IR) directly:

```
.java --javac -g -Xjcov--> .class --java.lang.classfile--> CFG + dominators
    --direct translator (BodyTranslator + ControlFlowStructurer)--> Laurel
        (+ CharacterRangeTable line:col / LineNumberTable line as positions)
    --Ion--> strataLaurel laurelAnalyzeBinary --> diagnostics
```

**The frontend never reads `.java` source text.** javac produces the
classfiles (the driver's compile step, exactly as any build would); from that
point on, every source position comes from bytecode debug attributes only --
`CharacterRangeTable` (line:column) and `LineNumberTable` (line) -- and the
filename from the `SourceFile` attribute. Positions have no byte offsets to
travel through Strata's offset-shaped protocol, so a (line, column) is packed
into a synthetic offset by `PositionCodec` on the way out and unpacked on the
way back; Strata only echoes it opaquely. Consequently ranges are
bytecode-native: the START line:column of a statement is exact, but the END
includes the trailing `;` (spans are statement-level), and sub-statement
anchors (a specific token, an argument, a lambda body) collapse to the
enclosing statement.

This round trip depends on Strata treating the offset as an **opaque token** --
storing, sorting, and echoing it, but never doing arithmetic on it. The
synthetic offset is `line << 10 | col` (the CharacterRangeTable's own packing),
not a real byte index, so any consumer that subtracted two offsets to compute a
length would corrupt positions silently. Nothing enforces this beyond the
corpus, so don't "optimize" a consumer to interpret an offset as a byte index.

There is no intermediate Java AST: instructions translate to Laurel
expressions on a simulated operand stack (`BodyTranslator`, with
`InvokeTranslator`/`SpecCallTranslator`/`FieldAccessTranslator` for calls,
JVerify spec calls, and field access), and structured control flow is
recovered from the CFG and emitted as Laurel statements (`ControlFlowStructurer`'s
region walker, with `LoopEmitter`/`SwitchEmitter`/`TryEmitter` per construct).
Declarations, contracts, and annotations are read straight off the classfiles
(`Declarations`, validated by `ContractValidator`), orchestrated by
`BytecodeToLaurelCompiler`.

Three input tiers are supported, detected from classfile attribute evidence
(`CompilationFacts`) and announced up front when degraded. Verdicts, counts,
and exit codes are identical across tiers (`TieredInputEquivalenceTest` pins
this); only position precision degrades:

- **FULL** (`-g -Xjcov`): CharacterRangeTable statement spans -- line:column
  diagnostic ranges (statement-level). What the driver's own
  javac step uses.
- **DEBUG** (`-g`): no statement spans -- diagnostics carry source-LINE
  precision (LineNumberTable fallback), column 1.
- **BARE** (default javac): additionally no local-variable names -- line
  precision plus synthetic local names (`v3`) in emitted Laurel. Known
  limitation: a local's declared type is reconstructed from its store
  opcode, which cannot distinguish the int family -- a branch-assigned
  `boolean` local types as `int32` and produces spurious Strata type
  errors (extra failures, never a false pass). DEBUG is unaffected.
- Input with no debug information at all (`-g:none`, obfuscated/stripped
  jars) is refused up front.

## Command line

`brazil-build installDist` builds
`bytecode-frontend/build/install/bytecode-verifier/bin/bytecode-verifier`:

```
bytecode-verifier [--interpret [--entry C#m] [--fuel N] [--interpreter core|laurel]]
                  [--emit-laurel FILE] [--show-ranges] [--no-constant-folding]
                  [--paths] [--verify-by-default=B] [--verbose] [--track-time]
                  [--strata DIR]
                  FILE.java... [--classpath P...] [--contract-path P...]
```

Diagnostic ranges are statement-level (see above), and `--emit-laurel` names
the file it wrote instead of counting errors. The input is compiled
against the command's own CLASS-retention build of the JVerify library, ahead
of `--classpath`, and its javac compiles no sources it finds on `--classpath`:
give them as input files. `--classpath` and `--contract-path` take every value
up to the next option, so give them after the input files. Strata runs as
`strataLaurel` from `PATH` (under `brazil-build`, the workspace's Strata: its
own build if it is checked out there), or, with `--strata DIR` or
`JVERIFY_STRATA=DIR`, as `lake exe strata` in `DIR/StrataCLI` (`DIR` being the
jverify repository's `Strata` submodule, or a Brazil workspace's `src` once
StrataCLI is built there).

Exit codes: 0 when every verified method verified (or the run completed), 4
when an assertion failed, 2 on a usage error or when there is no verdict (a
front-end error), and Strata's own exit code when the backend crashed.

`--no-constant-folding`, this frontend's own option, compiles the input
without javac's constant folding where possible (`:bytecode-unfolding`), so
`check(7 / 2 == 3)` reaches the translation as a division rather than as
`check(true)`. Constants that decide a statement's control flow, such as the
condition of `if ((6 & 3) == 2)`, stay folded.

## Modules

- **`bytecode-frontend`** -- classfile analysis (CFG/dominators/CRT/locals,
  `org.strata.java.bytecode.frontend`), the direct Laurel translator and
  driver (`org.strata.java.bytecode.verifier`), the `bytecode-verifier`
  command line (`org.strata.java.bytecode.verifier.Main`), and the test engine
  (`org.strata.java.bytecode.testengine`).
- **The CLASS-retention spec library** is built by isolated tasks in this
  module's `build.gradle.kts` block (`generateBytecodeLibSources` ->
  `compileBytecodeLib` -> `bytecodeLibJar`), not as a separate Gradle module.
  It has no hand-written sources: they are *generated* from `:library`. It is
  deliberately **not** a module -- see below.
- **`bytecode-unfolding`** -- javac without constant folding, for
  `--no-constant-folding` (`org.strata.java.bytecode.unfolding`).

## What it reuses

- **Laurel AST + Ion serialization + driver support** (`org.strata.jverify.laurel.*`,
  `JVerifyResults`, `SourceFile`, `IntervalTree`, `DiagnosticWithRange`,
  ...) come from `:verifier`, which also hosts the verification corpus.
- **`@JVerifyTest`, `TestMarkup`, `Blocker`, `JVerifyTestRecord`** come from
  `:test-engine`; the engine that runs them (`BytecodeTestEngine`) lives here.
- **The spec library** is `:library`, built a second time with `CLASS`
  retention so `@Pure`/`@Nat`/`@Unbounded`/`@Contract` survive into the
  classfiles the frontend reads. The `generateBytecodeLibSources` task copies
  `:library`'s sources at build time, rewriting only the retention token, and
  `compileBytecodeLib`/`bytecodeLibJar` compile them into a plain jar that this
  module's jar carries as a resource. The driver compiles every input against
  it (`VerifierDriver.specLibraryLocation`), the tests included; the artifacts
  land under `build/bytecode-lib/` and nothing is checked in. It is
  deliberately **not** a Gradle module: it shares package `org.strata.jverify`
  with `:library`, and Brazil puts every module jar on every other module's
  compile classpath, so a second module exporting that package would split it
  across two JPMS modules and break the `module-info`'d builds (e.g.
  `contracts2jqwik`). `:library` itself keeps `SOURCE` retention, so the
  annotations do not end up in the classfiles of user builds.

### Test engine isolation under Brazil

Brazil flattens every module's output onto a shared test runtime classpath, so
`BytecodeTestEngine` (registered via `@AutoService`) is *visible* to other
modules' test tasks. It stays inert unless activated by the `bytecode.corpus.root`
system property, which only `corpusTest` and `examplesTest` set -- and it is
passed as a real `-D` launch argument (not `Test.systemProperty`) so it cannot
leak into a reused Gradle worker and spuriously activate the engine elsewhere.

## Tests

- `brazil-build :bytecode-frontend:test` -- the bytecode frontend's own unit
  tests (frontend, driver, CLI, backend smoke), plain JUnit.
- `brazil-build :bytecode-frontend:corpusTest` -- runs the `@JVerifyTest`
  verification corpus (compiled `:verifier` test classes) through the bytecode
  frontend. Known gaps are `expectedToFail` with a `Blocker` on the fixture
  itself. Wired into `check`.
- `brazil-build :bytecode-frontend:examplesTest` -- the same for the
  `@JVerifyTest` examples in `:examples`. Wired into `check`.

The corpus oracle compares diagnostic positions at LINE level (start line),
since bytecode ranges are statement-level; verdicts, counts, exit codes, and
diagnostic messages are matched exactly. A diagnostic about an annotation
(`@Contract`/`@Impure` -- annotations have no source position in bytecode) is
anchored at the class declaration line, and one about a bodyless interface
method has no position at all (no `Code` -> no `LineNumberTable`).
Build with `brazil-build` (it puts `strataLaurel`/`z3`/`cvc5` on `PATH`); a plain
`gradlew` run cannot launch the Strata backend.
