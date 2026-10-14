# bytecode-frontend

A prototype **bytecode-based** Strata frontend, hosted inside the
StrataJavaFrontEnd (JFE) package as sibling Gradle modules. Where JFE compiles
Java *source* to Laurel (Strata's IR) through javac internals, this frontend
translates **JVM classfiles** directly:

```
.java --javac -g -Xjcov--> .class --java.lang.classfile--> CFG + dominators
    --direct translator (BodyTranslator + ControlFlowStructurer)--> Laurel
        (+ CharacterRangeTable line:col / LineNumberTable line as positions)
    --Ion--> strata laurelAnalyzeBinary --> diagnostics
```

**The frontend never reads `.java` source text.** javac produces the
classfiles (the driver's compile step, exactly as any build would); from that
point on, every source position comes from bytecode debug attributes only --
`CharacterRangeTable` (line:column) and `LineNumberTable` (line) -- and the
filename from the `SourceFile` attribute. Positions have no byte offsets to
travel through Strata's offset-shaped protocol, so a (line, column) is packed
into a synthetic offset by `PositionCodec` on the way out and unpacked on the
way back; Strata only echoes it opaquely. Consequently ranges are
bytecode-native: the START line:column of a statement matches JFE, but the END
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
  diagnostic ranges (statement-level; start matches JFE). What the driver's own
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
                  [--emit-laurel FILE] [--show-ranges]
                  [--paths] [--verify-by-default=B] [--verbose] [--track-time]
                  [--strata DIR]
                  FILE.java... [--classpath P...] [--contract-path P...]
```

The options and the report format are those of JFE's `verifier`, but the
diagnostic ranges are statement-level (see above), JFE's
`--filter-position` and `--include-filter-dependencies` are not implemented,
and the input is compiled against the command's own CLASS-retention build of
the JVerify library, ahead of `--classpath`. Unlike JFE's, its javac compiles
no sources it finds on `--classpath`: give them as input files.
As in JFE, `--classpath` and `--contract-path` take every value up to the next
option, so give them after the input files, and Strata runs as `strata` from
`PATH` (under `brazil-build`, the workspace's StrataCLI: its own build if it is
checked out there), or, with `--strata DIR` or `JVERIFY_STRATA=DIR`, as
`lake exe strata` in `DIR/StrataCLI` (`DIR` being the jverify repository's
`Strata` submodule, or a Brazil workspace's `src` once StrataCLI is built there).

## Modules

- **`bytecode-frontend`** -- classfile analysis (CFG/dominators/CRT/locals,
  `org.strata.java.bytecode.frontend`), the direct Laurel translator and
  driver (`org.strata.java.bytecode.verifier`), and the test engine
  (`org.strata.java.bytecode.testengine`).
- **The CLASS-retention spec library** is built by isolated tasks in this
  module's `build.gradle.kts` block (`generateBytecodeLibSources` ->
  `compileBytecodeLib` -> `bytecodeLibJar`), not as a separate Gradle module.
  It has no hand-written sources: they are *generated* from `:library`. It is
  deliberately **not** a module -- see the retention note below.

## Relationship to JFE (one-way, JFE is unaware)

The dependency arrow points only from `bytecode-*` to JFE modules; no JFE module,
source file, or test references the bytecode frontend. Concretely:

- **Laurel AST + Ion serialization + driver support** (`org.strata.jverify.laurel.*`,
  `JVerifyResults`, `SourceFile`, `IntervalTree`, `DiagnosticWithRange`,
  ...) are **reused from `:verifier`**, not copied -- so the Laurel schema can
  never drift out of sync.
- **`@JVerifyTest`, `TestMarkup`, `Blocker`, `JVerifyTestRecord`** are reused
  from `:test-engine`. Only the engine that *drives the bytecode pipeline*
  (`BytecodeTestEngine`) and the xfail manifest are bytecode-frontend-owned.
- **The spec library** is the one place JFE and the bytecode frontend genuinely differ:
  annotation retention must be `CLASS` (not JFE's `SOURCE`) so `@Pure`/`@Nat`/
  `@Unbounded`/`@Contract` survive into classfiles where the frontend reads
  them. Rather than maintain a second copy, the `generateBytecodeLibSources`
  task copies `:library`'s sources at build time, rewriting only the retention
  token, and `compileBytecodeLib`/`bytecodeLibJar` compile them into a plain
  jar used *only* as the driver's `javac -g -Xjcov` classpath: the tests pass
  it by path, and the command carries it as a resource of its jar (the
  artifacts land under `build/bytecode-lib/` -- generated sources, classes, and
  `bytecode-frontend-lib.jar`; nothing is checked in). It is deliberately
  **not** a Gradle module: it shares package `org.strata.jverify` with
  `:library`, and Brazil puts every module jar on every other module's compile
  classpath, so a second module exporting that package would split it across
  two JPMS modules and break JFE's own `module-info`'d builds (e.g.
  `contracts2jqwik`). Flipping `:library` to `CLASS` instead would put the
  annotations into every user's classfiles, where JFE trusts `@Nat` and
  `@Unbounded` on dependencies it never verified.

### How isolation holds under Brazil

Brazil flattens every module's output onto a shared test runtime classpath, so
`BytecodeTestEngine` (registered via `@AutoService`) is *visible* to JFE's own test
tasks. It stays inert unless activated by the `bytecode.corpus.root` system
property, which only `corpusTest` sets -- and it is passed as a real `-D` launch
argument (not `Test.systemProperty`) so it cannot leak into a reused Gradle
worker and spuriously activate the engine under, e.g., `:examples:test`.

## Tests

- `./... :bytecode-frontend:test` -- the bytecode frontend's own unit tests (frontend, driver,
  backend smoke), plain JUnit.
- `./... :bytecode-frontend:corpusTest` -- runs **JFE's `@JVerifyTest` verification
  corpus** (compiled `:verifier` test classes) through the bytecode frontend, a
  single source of truth shared with JFE. Bytecode-specific expected failures
  live in
  [`bytecode-xfails.txt`](src/main/resources/org/strata/java/bytecode/testengine/bytecode-xfails.txt),
  never as edits to JFE's fixtures. Wired into `check`.

The corpus oracle compares diagnostic positions at LINE level (start line),
since bytecode ranges are statement-level and column/end precision diverges
from JFE by design; verdicts, counts, exit codes, and diagnostic messages are
still matched exactly. A few fixtures remain xfail'd for positions JFE derives
from a construct that carries no line information in the classfile and so
cannot be reproduced at all: a diagnostic anchored at an annotation
(`@Contract`/`@Impure` -- annotations have no source position in bytecode) or
at a bodyless interface method's declaration (no `Code` -> no `LineNumberTable`).
These are position-only, never a verdict or count gap.

The xfail manifest is the single source of truth for the remaining divergences
versus JFE (each entry documents why). Build with `brazil-build` (it puts
`strata`/`z3`/`cvc5` on `PATH`); a plain `gradlew` run cannot launch the Strata
backend.
