# Design: Java exception support in JVerify

## Problem

JVerify rejects every exception-shaped construct today, and it rejects them
accidentally rather than deliberately:

- `throw` and `try`/`catch`/`finally` have no cases in
  `JavaToLaurelCompiler.convertStatement`; they fall into the default
  `"Unsupported statement: JCThrow"` / `"... JCTry"` and the per-method catch
  in `translateStaticMethod` marks the whole method Skipped.
- `throws` clauses on method signatures are silently ignored. Every emitted
  `Procedure` carries `throwsType/throwsBinding = empty, throwsOn = []`
  (see `translateStaticMethod`).
- The contract DSL (`JVerify.precondition/postcondition/modifies/...`) has no
  exceptional form: a method's contract describes its normal exit only.

Meanwhile the target language is ready. Laurel has a complete exceptional
channel — `throws (e: T)` on procedures, exceptional behavior cases
`throwsOn C { ensures P modifies M }` with forcing semantics and a checked
exhaustiveness claim, and statement forms `throw`, `try`/`catch` (typed
guards), `try`/`finally` — all lowered inside Strata by `EliminateExceptions`
into an ordinary `Result` carrier before any heap pass. The generated Java AST
bindings under `verifier/src/main/java/org/strata/jverify/laurel/` already
carry every node this design needs (`Procedure.throws_`/`throwsOn`,
`StmtExpr.Throw`/`TryCatch`, `CatchClause`, `CatchGuard`, `FinallyClause`,
`ThrowsOnBlock`).

So the work is entirely in this package: translate Java constructs onto
existing Laurel nodes, and give contracts an exceptional vocabulary. Every
established exceptional-contract form should be either supported or
deliberately absent with a documented reason.

## Facts on the ground

### Laurel's exceptional contract, in one paragraph

A procedure declares at most one exceptional exit: `throws (e: T)` names the
thrown value and types it at `T` (one type — Java multi-throws must join).
Exceptional behavior is stated in cases: `throwsOn C { ensures P modifies M }`
means the pre-state condition `C` *forces* a throw (`C ==> isBad`), `P` holds
on that throwing path (`C ∧ isBad ==> P`), and only `M` may change there.
Stating any case claims the enumeration is complete: Strata emits a checked
`isBad ==> (C₁ ∨ … ∨ Cₙ)`, so a throwing path no guard covers is an error
rather than silently unframed. Consequences worth internalizing before
touching the DSL:

- `throwsOn true` = "always throws"; `throwsOn false` = "never throws";
  stating *no* case leaves throwing paths unconstrained (and unframed) —
  three distinct contracts.
- Guards are pre-state predicates over parameters; they may not read the heap
  (Strata's Resolution rejects that shape — the lowering places guards in
  postconditions, so a heap read would silently mean post-state).
- There is deliberately no unforced "if it throws then P" clause and no
  wildcard case frame; see §4.

### What the frontend gives us to work with

- Composites: classes referenced by translated code become Laurel `composite`
  types (`referencedCompositeTypes` in `JavaToLaurelCompiler`) —
  but always with an empty `extending` list, although the `CompositeType`
  binding has the slot. Exception hierarchies need real `extends` chains:
  catch guards lower to `e is T` tests and multi-throws joins need common
  ancestors.
- `new E(...)`: `convertExpression`'s `JCNewClass` case
  emits `StmtExpr.New` and discards
  the constructor arguments. Thrown values that carry data (`badIndex`-style
  fields) depend on how constructor translation evolves; phase 1 sequences
  around it (see Phasing).
- `instanceof` on opaque reference types is rejected
  (see `convertExpression`). Catch dispatch does not need it —
  catch guards are generated on the Laurel side — but user code testing a
  caught exception's type does. Out of scope here; noted as a follow-up.
- javac's LOWER phase (already in the pipeline, `JavaLowerer.java`) desugars
  some sugar for us — including try-with-resources into plain try/finally
  (verified; see Decision 4 and the `TryWithResources` fixture).
- The internal `throw new ContractException()` idiom (contract-only bodies,
  `ExternalContractCompiler.isContractThrow`) never reaches
  `convertStatement`; adding a real `JCThrow` case does not disturb it.

## Design

### 1. Signatures: the `throws` clause becomes the declared exceptional exit

A method's Java `throws` clause is the single source of the declared
exceptional type. For `throws E`, the procedure gets
`throwsType = E, throwsBinding = e` (binding name synthesized; Strata
freshens against collisions). For `throws E1, E2`, the declared type is the
least common ancestor of `E1..En` among the *modeled* classes, and the precise
set is recoverable per case via type claims in case postconditions. Each
thrown class is emitted as a composite with its superclass chain linked all
the way up to `Throwable` — not merely up to the join — because a *caller*
may catch any supertype of the declared type, and that guard's `is` test
needs the two types related no matter where they sit (pinned by
`supertypeCatchOfPropagated`).

The join silently widens when the declared types are unrelated (up to a broad
JCL ancestor like `Exception`); a widening lint is deliberately deferred to
the DSL phase, where the per-case type claims can state the precise set.

A `throw` statement in a method with no `throws` clause is a per-construct
diagnostic ("declare the thrown type in the method's `throws` clause"), not a
method skip. Java permits undeclared unchecked throws; JVerify requires the
declaration because the declared type is where the whole exceptional contract
hangs.

### 2. Statements

| Java | Laurel | Notes |
|---|---|---|
| `throw expr;` | `StmtExpr.Throw` | Requires a declared `throws` (above). |
| `try { } catch (E e) { }` | `TryCatch` with one `CatchClause`, guard `e is E` | Guard generated structurally; no Java `instanceof` involved. |
| multi-catch `catch (A \| B e)` | one `CatchClause`, guard `e is A \|\| e is B` | The binding is typed at the LCA, as in Java. |
| several `catch` arms | one `CatchClause` per arm, in order | Laurel tries guards in clause order; Java's "first applicable arm" semantics carries over directly. |
| `try { } finally { }` | `FinallyClause` | Laurel runs the arm on normal, thrown, and `return`/`break` unwinding paths — same as Java; nothing to encode manually. |
| try-with-resources | desugared to try/finally by javac LOWER | See Decision 4: the desugared re-throw needs the declared-throws rule satisfied. |
| re-throw from a catch arm | `Throw` inside the arm | Already covered by Laurel's widened-propagation lowering. |

The `break`/`continue`/`return`-through-`finally` interaction is Laurel's
job (its `EliminateExceptions` threads unwinding flags through `finally`
arms); the frontend maps structure only.

### 3. The exceptional contract DSL

One new marker method on `JVerify` — `throwsOn` — whose case clauses chain
off the `ThrowsOnCase` handle it returns, reusing the existing clause
vocabulary scoped to the case. This mirrors Laurel's block structure exactly
(Laurel reuses `ensures`/`modifies` inside a `throwsOn` block; the JFE reuses
`postcondition`/`modifies` behind a receiver), and it makes attachment
structural: a case is one statement, and a clause without its case does not
compile.

```java
static int divide(int a, int b) throws ArithmeticException {
    JVerify.throwsOn(b == 0)                                        // guard: forces the throw
        .postcondition((ArithmeticException e) -> a != 0 || true)   // property of that path
        .modifies(logCell);                                          // frame of that path
    ...implementation...
}
```

- `throwsOn(boolean condition)` — opens an exceptional behavior case; the
  condition is the case's guard. Lowered to a `ThrowsOnBlock` on
  `Procedure.throwsOn`. Multiple cases = multiple statements. `throwsOn(false)`
  states the method never throws.
- `.postcondition(Predicate<E> property)` — exceptional postcondition of the
  case; the lambda's parameter binds the thrown value. The parameter must be
  typed at the declared throws type (the multi-throws join); per-type claims
  narrow *inside* the property with `instanceof` and a cast —
  `.postcondition((AppException e) -> implies(e instanceof ParseError, ((ParseError) e).code > 0))`
  — which is exactly how Laurel's own per-type claims are written
  (`e is T ==> (e as T)…`), and both operators are supported for exception
  types in code positions too. A subtype-typed parameter is refused with the
  narrowing idiom in the diagnostic: it would need an implicit implication
  wrapper plus occurrence-wise casts, a lowering with no other consumer,
  whereas `instanceof`/cast support pays for itself in catch arms as well.
  Several `.postcondition` clauses in one case conjoin.
- `.modifies(Object target)` — exceptional frame entry of the case;
  repeatable, same argument forms as the normal `modifies` (including
  `everything()`).
- No ordering diagnostics are needed: a clause without a case is uncompilable
  (the clause methods exist only on `ThrowsOnCase`), and normal-exit clauses
  may appear anywhere in the header because chains and flat calls cannot be
  confused. Clause-level chaining is a first for the contract surface, chosen
  deliberately; the expression level already chains spec values
  (`sequence(arr).take(i).contains(x)`).

Like every JVerify marker, the chain is a no-op at runtime.

### 4. Deliberate strictness

Two deliberate strictness choices:

- **No unforced exceptional postcondition.** There is no implication-only
  "if it throws then P" clause: a case's guard *forces* its throw, and
  forcing contracts are strictly stronger. The implication form can be added
  later as a Laurel surface feature if demanded.
- **No default "anything may be thrown".** Stating no case leaves throwing
  paths unconstrained but still typed by the declared `throws`, and stating
  any case claims exhaustiveness.

Both follow from Laurel's semantics and are features, not gaps: they are what
make a caller able to prove a handler dead or a frame preserved across a call
it can show does not throw.

### 5. Unchecked and implicit exceptions

Unchanged policy: implicit runtime exceptions (null dereference, division by
zero, array bounds, cast failure) remain proof obligations — the verifier
requires their absence rather than modeling them as catchable events.
Consequences:

- `catch (NullPointerException e)` around code whose NPE-freedom is proven is
  dead by construction; today such an arm is translated as-is and its guard
  simply never holds (nothing explicitly thrown reaches it). A dead-arm lint
  is future work, not implemented in these phases.
- Explicitly *throwing* an unchecked exception is fine — declare it in the
  `throws` clause (legal Java, see §1) and it participates like any other.

## Decisions

1. **Declared-throws discipline**: every throwing method declares its thrown
   types in the Java `throws` clause, unchecked included. The declaration is
   the anchor for `throwsType` and the DSL.
2. **Multi-throws join**: LCA over modeled composites, with `extends` chains
   emitted. Requires exception classes (and intermediate ancestors) to be
   user-defined or modeled; JCL exception types only as far as
   builtin-contracts model them.
3. **Unforced exceptional postconditions not offered** (deliberate
   strictness, see §4).
4. **try-with-resources**: javac LOWER (already applied) desugars it to
   try/finally in our configuration — verified, pinned by the
   `TryWithResources` fixture. The catch is the desugared
   `catch (Throwable t) ... throw t`: the re-throw answers to the
   declared-throws rule, so the form is refused in methods that cannot cover
   `Throwable`. A friendlier per-construct diagnostic is future work.
5. **Interpret mode out of scope**: thrown values are heap composites and the
   interpreter has no heap; exception tests are verify-mode only, matching
   the Laurel test suites' split.
6. **Exceptions carrying data**: blocked on constructor-argument translation
   (`JCNewClass` discards args today). Phase 1 supports no-arg exception
   construction plus field writes after `new`; constructor-carried data joins
   when constructor translation does.

## Phasing

Each phase is a separately buildable, separately testable CR; the DSL doc
(this file) rides with the first.

1. **Signatures** — `throws` clause → `throwsType`/`throwsBinding`, exception
   composites with `extending`, LCA join. Bodiless/opaque throwing methods
   verify against the derived `isBad ==> err is T`; callers see the
   exceptional exit. No statements yet.
2. **`throw`** — `JCThrow` → `StmtExpr.Throw` plus the declared-throws
   diagnostic. Unblocks `SwitchDesugarer`'s switch-rule-throw skip
   (PLAN.md §3 step 2b) as a side effect.
3. **`try`/`catch`/`finally`** — `JCTry` → `TryCatch`/`CatchClause`/
   `CatchGuard`/`FinallyClause` per the statement table; try-with-resources
   per Decision 4.
4. **DSL** — the three markers, contract-compiler parsing with the ordering
   diagnostics, `Procedure.throwsOn` emission. Tests mirror Strata's
   `ThrowsOnClause.lean` suite from the Java side: forcing
   positive/negative, per-case frames, exhaustiveness, caller-side handler
   dead/live pairs.
5. **Test infrastructure & docs** — `Blocker` bucket for exceptions,
   `tests/javasupport/exceptions/` suite, xfail markers for later phases so
   every CR lands green; user-guide section covering the exceptional
   contract forms.
