# JFE test-suite parity matrix

Status of the bytecode frontend (`bytecode-frontend`) against StrataJavaFrontEnd's
(JVerify's) verification test corpus. The corpus is **shared in place, never
copied**: `:bytecode-frontend:corpusTest` runs JFE's compiled `:verifier` test
classes (`verifier/src/test/java/org/strata/jverify/verifier/tests/**`)
through the bytecode frontend. JFE is the single source of truth; this module
never edits JFE's fixtures. JFE's own driver unit tests under
`verifier/src/test/java/org/strata/jverify/verifier/` are not `@JVerifyTest`
classes, so the engine never claims them.

**skip** = tests that JFE itself skips or marks `expectedToFail`; the xfail
inversion handles them identically, so a skip is *parity*, not a gap.
Bytecode-frontend-specific deviations from the shared annotations live in
[the xfail manifest](../src/main/resources/org/strata/java/bytecode/testengine/bytecode-xfails.txt)
owned by this module: `XFAIL` entries are known bytecode gaps on tests JFE
verifies (counted in the skip column), and `RUN` entries run tests JFE marks
`expectedToFail` when the source-frontend gap does not apply here. The
engine's XPASS tripwire covers manifest entries too, so a cleared gap fails
loudly until its manifest line is removed.

Snapshot after parity round 4 -- shared corpus, current JFE mainline and
Laurel Ion schema (`typeArgs`, `ModifiesGroup` frames, `Field.initializer`),
WITH exception support: throw statements, try/catch/finally (including
multi-block and catch+finally fusion in the decompiler), throws-clause
joins, exception composites with extends chains, instanceof/casts on
exception types, and the `throwsOn(...)` exceptional-contract DSL. All 14
`javasupport/exceptions` classes now behave identically to JFE (10 pass; 4
Wishlist* fixtures are expectedToFail in JFE too).
ResolutionErrorsStringMethods also passes: the missing-contract warning for
library instance calls and the varargs array-initializer refusal are
reproduced at JFE's exact positions.

Round 5 (mainline sync): resynced onto current JFE mainline, which added the
`RefusalGates` feature and 57 new/changed corpus fixtures. Mainline now emits
per-method refusal diagnostics (`warning: instance method 'X' was not
verified: <reason>` paired with `error: call to 'X', which could not be
translated`) that the bytecode frontend does not reproduce; because the
corpus is scored at whole-class granularity, these are recorded as `XFAIL`
manifest entries rather than partial passes. 42 such fixtures were added to
the manifest (grouped FRONTEND_OVERLOAD, FRONTEND_DISPATCH,
FRONTEND_DIAGNOSTIC, FRONTEND_INSTANCE_METHODS, STRATA_FRESH,
FRONTEND_OTHER). Current `:bytecode-frontend:corpusTest` standing:
**65 pass / 110 skip / 0 fail** across 175 `@JVerifyTest` test cases. (The
class-level round-4 snapshot below predates these new fixtures.)
Per-category snapshot from round 2 (pre-exceptions corpus, 130 classes) for
reference:

| category | pass | skip |
|---|---|---|
| examples | 13 | 1 |
| interpret | 7 | 1 |
| javasupport | 3 | 4 |
| javasupport/classes | 4 | 5 |
| javasupport/expressions | 11 | 12 |
| javasupport/generics | 2 | 6 |
| javasupport/interfaces | 1 | 2 |
| javasupport/lambdas | 1 | 1 |
| javasupport/lambdas/namecollision | 1 | 0 |
| javasupport/localClasses | 1 | 1 |
| javasupport/nestedClasses | 1 | 2 |
| javasupport/packages | 0 | 1 |
| javasupport/records | 2 | 5 |
| javasupport/statements | 7 | 2 |
| javasupport/wildcards | 1 | 1 |
| jcl | 0 | 6 |
| verification | 2 | 3 |
| verification/abstractContracts | 1 | 2 |
| verification/externalcontracts | 3 | 4 |
| verification/pure | 2 | 4 |
| verification/shouldVerify | 0 | 4 |
| **total** | **63** | **67** |

Round-2 history: the initial bytecode frontend had 25 gaps (40 pass). All
but three were closed by porting JFE's method-count model, nested-class
sort qualification, explicit `modifies(...)`, three Decompiler
increment-recovery bugs, `@Nat`/`@Unbounded` type-annotation recovery from
`RuntimeInvisibleTypeAnnotations`, external `@Contract` validation, switch
desugaring, and `while(true)`+`break` structuring fixes.

## Remaining bytecode-frontend-specific entries (2)

Both are recorded as `XFAIL` entries in the manifest, not as edits to JFE's
fixtures, and both are fundamental to the bytecode strategy rather than
unfinished translation work.

### 1. PrimitiveCasts — blocked by a javac bug
`javac -Xjcov` throws in `CRTable.SourceComputer` on annotated-type casts
like `(@Nat int) x` (JDK 25; reproduced with the plain CLI javac 25.0.4 --
it is an unconditional `Assert.error`, CRTable simply has no case for
`JCAnnotatedType`, so no assertion flag avoids it). Inherent to the
`-g -Xjcov` compilation strategy; we degrade to a clean compile-error
diagnostic. Stripping the cast annotations from the source before compiling
is not an option: an `(@Unbounded int)` cast compiles to no instruction --
the type annotation is its only trace, and it is exactly what the test
verifies. Needs a JDK fix.

### 2. TranslatorSkip — intentional divergence (we do MORE than JFE)
JFE refuses multi-init `for` loops at the source level; javac desugars them
before bytecode, so this frontend translates and verifies them successfully.
The test pins JFE's refusal message, which cannot (and need not) be
reproduced from bytecode. The catch-wrapper behavior it really covers is
exercised by our unstructured-control-flow skip path.

## Notes on adopted tests

- `AllMatchContract` passes here while JFE marks it `expectedToFail` (a
  `RUN` manifest entry). The pass is currently vacuous (instance-method
  bodies are not translated, so its declared counts are met without
  exercising the contract machinery); revisit when instance-method
  translation lands.

## Where the big skip blocks would open up
1. Builtin contracts (`jcl/` 6 skips + ResolutionErrorsStringMethods).
2. Instance-method translation (many `verification/shouldVerify`,
   `javasupport/classes` skips are blocked on FRONTEND_INSTANCE_METHODS).
3. Floats/doubles (`javasupport/expressions` skips: Floats, Doubles,
   FloatOperators, DoubleOperators).
