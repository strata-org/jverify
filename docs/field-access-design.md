# Design: Supporting instance-field access in `JavaToLaurelCompiler`

> **Status.** This document was written *before* the CR that ships
> instance-field-access support. References to what the compiler does
> "today" (composites emitted with empty field lists, `modifies` clauses
> silently dropped, etc.) describe the pre-CR state of `mainline`, not
> the post-merge state. The `### Modifies clauses are still not emitted`
> and `### `modifies` clauses on opaque procedures` sections below
> describe work that this CR itself delivered. Kept intact for
> historical context.

## Problem

JVerify cannot analyze any Java program that reads or writes an instance
field. The trivial reproducer at the repo root is:

```java
class C {
    int x;
    int y;
}

class CUser {
    static void writesToX(C c) {
        c.x = 2;
    }
}
```

which fails with

```
Foo.java(7:21-7:22): error: Unsupported expression: JCFieldAccess
```

The message anchors to the LHS `c.x`. On the RHS the same node type (a
`JCFieldAccess` reading `c.x`) hits the identical `default` arm.

The `default -> throw` sits at
`verifier/src/main/java/org/strata/jverify/verifier/compiler/generator/laurel/JavaToLaurelCompiler.java:847`.
The only `JCFieldAccess` case above it (`:797`) handles the special case
of `Foo.CONST` where `constValue()` is non-null — it collapses the node
to its baked-in integer literal, which does not go through Laurel's
field-access mechanism at all.

## Facts on the ground

### The Laurel wire format already models fields

The Java-side Laurel AST already contains everything a field read / write
needs:

- Read: `StmtExpr.FieldAccess(obj: StmtExpr, field: String)` at
  `verifier/src/main/java/org/strata/jverify/laurel/StmtExpr.java:154-168`.
  Factory: `Laurel.fieldAccess(sr, obj, field)`
  (`Laurel.java:67-68`). Ion op `Laurel.fieldAccess`.
- Write LHS: `AssignTarget.AssignTargetField(obj: String, field: String)`
  at `AssignTarget.java:35-49`. Factory: `Laurel.assignTargetField`
  (`Laurel.java:85-86`). Ion op `Laurel.assignTargetField`. Note the
  target here is a *bare identifier* (`String`), not a general expression
  — so only one-level writes `obj.field = ...` are wire-representable
  via `assignTargetField`.
- Alternative write path: `StmtExpr.Assign(target, value)` accepts a
  `FieldAccess` as its target. Strata's parser translates
  `Laurel.assign` with a `fieldAccess` LHS into an
  `.Assign [Field ...]` node
  (`Strata/…/Grammar/ConcreteToAbstractTreeTranslator.lean:265-271`).
  This path admits *nested* field writes like `a.b.c = v` because the
  target is a full `StmtExpr`.
- Composite type declarations already model fields:
  `Field.MutableField(name, fieldType)` and
  `Field.ImmutableField(name, fieldType)` at
  `verifier/src/main/java/org/strata/jverify/laurel/Field.java`.
  `Composite.Of` (`Composite.java:4-7`) carries a `List<Field>`.

### Strata already handles heap-effectful field access

`HeapAnalysis.lean:52-72` classifies procedures by direct heap effects:

- `.Var (.Field _ _)` → reads heap.
- `.Assign` with any `.Field` target → writes heap.
- `.New _` → writes heap.
- `.IncrDecr` on a `.Field` target → writes heap.

`HeapParameterization.lean:171-282` then rewrites the procedure body:
each field read becomes `Box…tVal!(readField(heap, obj, "Type.field"))`
and each field write becomes `heap := updateField(heap, obj, "Type.field", BoxT(v))`.
Heap-reading callees get a `heap` argument threaded in; heap-writing
callees get `heap` threaded in *and out*.

The key precondition is `resolveQualifiedFieldName`
(`HeapParameterization.lean:155-159`): it queries `SemanticModel` for
each field name and expects it to resolve to a `.field owner _`
declaration. If a program calls `c.x` but no composite has a field
named `x`, resolution fails and the whole pipeline breaks.

**Consequence.** Emitting `Laurel.fieldAccess` from `JavaToLaurelCompiler`
is not enough on its own — the corresponding composite must declare the
field, so `SemanticModel` can resolve it. Today
`JavaToLaurelCompiler.java:86-91` always emits opaque composites with
empty field lists (`composite(typeName, Optional.empty(), List.of(), List.of())`).

### Modifies clauses are still not emitted

`JavaToLaurelCompiler.java:474` documents that `modifies` is always
empty (jverify does not yet emit modifies clauses). The
`MethodOrLoopContractCompiler` already parses `modifies(...)` and
`reads(...)` clauses (`MethodOrLoopContractCompiler.java:354`), but
they're dropped on the floor.

For a *transparent* procedure (Strata sees the whole body) this is
fine: the body directly reads / writes what it reads / writes, and
`HeapAnalysis` picks that up. For an *opaque* body (the default for
non-`@Pure` procedures — `JavaToLaurelCompiler.java:475-478`), a
missing `modifies` clause means Strata cannot know *which* fields the
procedure disturbs across a call. Every call-site would have to assume
"anything could have changed", which drowns the caller in unprovable
obligations. **This is the biggest verifier-side gap unblocked by the
raw AST change.**

### Existing precedent for reference types

For reference types today, `translateType`
(`JavaToLaurelCompiler.java:214-224`) already computes a stable
composite name (`fully-qualified, '$' -> '.'`) and stashes it in
`referencedCompositeTypes`. Composite declarations are emitted in
insertion order in the header of the first Laurel file
(`JavaToLaurelCompiler.java:86-91`).

`ClassesExtendingClassesVerification.java` mentions `this.x`
in a `postcondition` today and asserts `methodsVerified = 11`
without a `skip=`. It works because the source is never analysed —
`this.x` never reaches `JavaToLaurelCompiler` (I traced the pipeline;
`PreconditionOfCompiler` inlines `precondition.of(x.foo(...))` calls
before laurel translation, and other paths that would keep it are
pruned earlier). This test is not exercising the field-access path
we need to unblock.

### `PreconditionOfCompiler`'s use of `JCFieldAccess`

`PreconditionOfCompiler.java:96` looks at `preconditionOwnerCall`'s
method-select to find the `this`-analogue when inlining
`precondition.of(receiver.method(args))`. This is a *sender-side*
introspection over the Java AST, not an emission target — it does
not go through `convertExpression`. So this design does not touch it.

## Non-goals for this design

- **Static field access** (`ClassName.staticField`). javac represents
  these as `JCFieldAccess` too, but the encoding is different (module-
  level Laurel binding, no heap plumbing). This design is scoped to
  instance fields; static fields become a separate follow-up.
- **Method references** (`obj::method`) and **fully-qualified type
  names in type position** (`java.lang.String`). These also parse as
  `JCFieldAccess` but never flow through `convertExpression` for a
  read (`MethodReferenceToLambdaCompiler` rewrites them; type refs
  are consumed by `translateType`). No change needed.
- **Chained instance-method calls via a field** (`a.b.method()`) work
  as soon as reads of `a.b` produce a Laurel field access — the call
  case at `:765-778` already recurses through `getMethodSelect()`. So
  they are covered incidentally, not as a separate design item.
- **Constructors that initialize fields.** `new C(...)` currently
  emits an opaque `new_(C)` value (`:799-832`). A follow-up will
  couple the constructor arg list to the composite's field
  declarations so `c.x` at the callsite equals the value passed to
  the constructor.
- **Arrays.** Array element reads/writes are `JCArrayAccess`, not
  `JCFieldAccess`. Out of scope.
- **`length` on arrays and Strings.** Modelled specially in the
  `MoveStaticMethodsToStaticType` / built-in contract path, not via
  the field-access mechanism. Out of scope.
- **Inherited fields.** `Composite.Of` supports `Optional<Extends>`
  and Strata's `IsType`/`AsType` traverse it. Emitting `extends`
  correctly is out of scope for this pass; we'll only emit fields
  declared *on* the current class. Inherited-field access will
  surface as a "field not found" resolution error, tracked as a
  follow-up. `ClassesExtendingClassesVerification` implicitly
  documents that we care about this eventually.
- **Compound assignment on fields** (`c.x += 1`, `c.x *= 2`). javac
  keeps these as `JCAssignOp`, not `JCAssign`, and the frontend
  rejects them up front with `Unsupported expression: JCAssignOp`.
  Support requires adding a `JCAssignOp` case that either lowers to
  `c.x = c.x + 1` (evaluation-order caveats) or emits a Laurel
  compound-assign primitive. Pre/post-`++`/`--` on fields *are* in
  scope for this CR (they're `JCUnary` and route through the
  existing increment/decrement handling).

## Proposal

### Overview

Two things need to happen together:

1. **On the Java frontend side (this package),** declare each class's
   instance fields on its composite, translate `JCFieldAccess` reads to
   `Laurel.fieldAccess`, and translate `JCAssign(JCFieldAccess, rhs)`
   writes to a `Laurel.assign` whose target is a `fieldAccess`. Also
   emit `modifies` clauses on opaque procedures.
2. **Strata / Laurel side: no changes needed.** Every piece of AST,
   analysis, and transform this design relies on already exists there.
   The heap encoding is stable.

### Field emission on composites

Extend `JavaToLaurelCompiler` to collect, for each referenced composite
type, the class's declared instance fields (`JCVariableDecl` under
`JCClassDecl` with `Flags.STATIC` clear). A new
`Map<String, ClassSymbol> referencedCompositeSyms` supplements
`referencedCompositeTypes`. On the first pass over each compilation
unit's `defs`, register the class symbol; on the composite-emission
loop (`:86-91`) look up the fields from the symbol and translate them.

Field mutability:
- `final` fields → `Field.ImmutableField`.
- Everything else → `Field.MutableField`.

Field types: reuse `translateType(type, mods)`. The same
`translateType` path already produces `CompositeType` for reference-
typed fields — which will transitively drive their own composites
to be declared (deterministic order preserved by `LinkedHashMap`
iteration and the existing `referencedCompositeTypes` set).

Unsupported field types (e.g. `float`, `double`, arrays) will throw
via `translateType`'s existing paths. That is the same behavior as
today for locals / params of those types, so no new user-visible
surface.

### Read translation

Add a new arm in `convertExpression` between the constant-`JCFieldAccess`
case (`:797`) and the default:

```java
case JCTree.JCFieldAccess fa when fa.sym instanceof Symbol.VarSymbol vs
        && (vs.flags() & Flags.STATIC) == 0 -> {
    // instance field read: c.x  →  Laurel.fieldAccess(c, x)
    SourceRange sr = toSourceRange(fa);
    StmtExpr obj = convertExpression(fa.selected, renames);
    // Ensure the owning composite is declared with this field so
    // Strata's resolver can find it.
    ensureFieldOnComposite(vs);
    yield fieldAccess(sr, obj, fa.name.toString());
}
```

`ensureFieldOnComposite(VarSymbol)` records `(ownerName, fieldName,
type, isFinal)` in a new `Map<String, LinkedHashMap<String, Field>>`
so the composite-emission loop can consult it. Owner name is derived
the same way as `translateType` derives composite sort names:
`vs.owner.enclClass().getQualifiedName().replace('$', '.')`.

The `renames` map does not apply to field names — `renames` is only
for the postcondition-lambda `result → $result` rebinding on the
enclosing local scope, not for field selectors.

### Write translation

Two cases live in `convertExpression`:

- `JCAssign` with a `JCFieldAccess` LHS (`c.x = v` — plain assignment
  only; compound assignments arrive as `JCAssignOp` and are handled
  separately, see Non-goals):

  ```java
  case JCTree.JCAssign asgn when asgn.lhs instanceof JCTree.JCFieldAccess fa
          && fa.sym instanceof Symbol.VarSymbol vs
          && (vs.flags() & Flags.STATIC) == 0 -> {
      SourceRange sr = toSourceRange(asgn);
      StmtExpr obj = convertExpression(fa.selected, renames);
      StmtExpr lhs = fieldAccess(toSourceRange(fa), obj, fa.name.toString());
      StmtExpr rhs = convertExpression(asgn.rhs, renames);
      ensureFieldOnComposite(vs);
      yield assign(sr, lhs, rhs);
  }
  ```

  Note this uses `Laurel.assign` (accepting a general `StmtExpr` target),
  not `Laurel.assignTargetField` (which takes a bare `String` obj and
  would restrict us to depth-1 writes only). Strata's parser accepts
  both spellings for depth-1; using `assign` uniformly means nested
  writes `a.b.c = v` work with no extra emission code.

- Existing `JCAssign` arm at `:759-760` stays untouched — it handles
  local-variable assignments, which do *not* hit the new guard.

Immutability check: writing to an `ImmutableField` (Java `final`)
must fail. Strata's `Resolution` already enforces this on `.Assign`
against a `Field.ImmutableField`. So no client-side check needed;
the emission is straightforward.

### `modifies` clauses on opaque procedures

Wire up the existing `contract.modifies()` list into the emitted
opaque spec. In `translateStaticMethod` (`:380-484`):

```java
List<StmtExpr> modifies = new ArrayList<>();
for (var mod : contract.modifies()) {
    modifies.add(convertExpression(mod.get()));
}
// ...
Optional<OpaqueSpec> optSpec = canStayTransparent
        ? Optional.empty()
        : Optional.of(opaqueSpec(ensures, modifies));
```

Each `mod.get()` is a `JCExpression`; for field-modifies it will be
either `c.x` (a `JCFieldAccess`, translating to `Laurel.fieldAccess`)
or a general expression. Strata's `ModifiesClauses.lean` accepts an
arbitrary `StmtExpr` and structural-matches on `.Var (.Field _ _)` to
key the modifies effect.

**Default when the user provides no modifies clause but the body
writes to fields**: emit no modifies for now (the spec remains the
same as today). This is unsound at call-sites — but no more unsound
than today, since today the procedure is *transparent-with-nothing-
to-write* anyway (it never reached this branch). We document the
gap in `upstream-divergences.md` under a new "Known limitations"
entry.

Follow-up (out of scope for this CR): default to `modifiesWildcard`
(everything) when a body writes to a field and no clause was
provided. That's the sound-and-noisy fallback; the alternative
(auto-inferring from body reads/writes) would duplicate Strata's
`HeapAnalysis` and drift over time.

### Reads / receiver purity

For method calls whose *receiver* is a field access (`a.b.c.method()`),
the existing call arm (`:765-778`) computes the callee name from the
symbol and translates each argument. The method-select `.b.c` is
consumed via `TreeInfo.symbol(...)`, so the receiver expression itself
is not re-translated as an expression in that arm — good; nothing to
change.

For record-component accessors (`someRecord.x()`), those are method
invocations under the hood, not `JCFieldAccess`. They flow through
the invocation arm today, so the design here does not affect them.

## Effects on the wire format

None. Every op and arg used by this design (`Laurel.fieldAccess`,
`Laurel.assign` with a fieldAccess target, `Laurel.mutableField`,
`Laurel.immutableField`, `Laurel.modifiesClause`, `Laurel.composite`
with a non-empty field list) is already emitted by upstream JVerify
or accepted by upstream Strata. The regenerate-Laurel-Java-AST
command in `CLAUDE.md` does not need to be run.

## Impact on other passes / tests

- **`insertFloatingPointCasts`** and **`insertImplicitCasts`** (the
  `jccast-handling` follow-up) walk field-access reads today with the
  base `TreeTranslator` defaults, so they will now see, but not need
  to transform, a `JCFieldAccess` read. Writes go through `JCAssign`
  whose LHS is a `JCFieldAccess` — likewise no change needed; the RHS
  gets cast-inserted by the same JLS §5.6 site logic that already
  handles local-variable assignment.
- **`DropUnreachableUnits`** at `frontend/DropUnreachableUnits.java:94`
  already has a `visitSelect` that keeps the enclosing top-level unit
  reachable when a field is referenced. No change.
- **Tests.** Add:
  1. A new test module
     `verifier/src/test/java/org/strata/jverify/verifier/tests/javasupport/expressions/FieldAccess.java`
     covering: bare read, bare write, read then write, write then read
     in the same method, chained read `a.b.c`, `final` field write
     (expect error). Each `@JVerifyTest` asserts `methodsVerified` /
     `errorCount` explicitly with hat annotations for expected errors.
  2. An assertion that `Foo.java` at the repo root verifies without
     error (as a smoke test — likely as a manual `./verify Foo.java`
     command run in the CR description rather than a checked-in test).
  3. Once field reads work, revisit `ClassesExtendingClassesVerification`
     to confirm no regression in its `methodsVerified = 11` count.

## Alternatives considered

- **Encode fields as extra procedure parameters (getters).** Rejected:
  it does not compose with mutation, does not model shared state, and
  reinvents a heap encoding Strata already has. Only viable for
  `@Pure` reads on stateless data.
- **Encode each field as a `Map<C, T>` global.** Rejected: this is
  essentially the manual version of what `HeapParameterization`
  already generates via `updateField`/`readField`. It would drop us
  outside the heap-analysis machinery and force JVerify to reimplement
  frame conditions.
- **Static fields alongside instance fields in this pass.** Rejected
  as scope creep: static-field encoding is either "an extra
  procedure-scoped variable" or "a top-level Laurel binding", both of
  which need their own analysis pass on the Strata side to interact
  correctly with the heap. Splitting keeps the CR reviewable and lets
  us ship instance-field support without waiting on static-field
  design.

## Estimated CR size

- `JavaToLaurelCompiler.java`: ~80 lines added (three convertExpression
  arms + `ensureFieldOnComposite` + composite-emission tweak +
  `modifies` wiring).
- New test file: ~150 lines (12–15 methods across the shapes above).
- No changes to `laurel/` (generated AST).
- No changes to `Strata/`.

One commit per logical piece: (a) read, (b) write, (c) composite
fields, (d) modifies wiring, (e) tests. Each intermediate step
passes its own subset of tests.
