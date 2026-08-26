# Design: Handling `JCCast` between primitive types in `JavaToLaurelCompiler`

## Problem

Java's AST represents every primitive type conversion — implicit or explicit
— as a `JCTree.JCTypeCast` node (e.g. `(int) someChar`, `(short) someInt`,
or the implicit narrowing in `byte b = 1; b += 1;`).

Today `JavaToLaurelCompiler.convertExpression` has no case for `JCTypeCast`.
It falls through to:

```java
default -> throw new JavaViolationException(
        "Unsupported expression: " + expr.getClass().getSimpleName());
```
(`JavaToLaurelCompiler.java:847`)

Two tests already exercise this path and fail today:
`verifier/src/test/java/org/strata/jverify/verifier/tests/javasupport/expressions/Operators.java:161-170`
(`castNumTrivial`, `castNumInvalid`).

We need a design that soundly translates every legal Java primitive cast.

## Facts on the ground

### Current primitive-type encoding

All Java integer widths and `char` are encoded as constrained `int` in
Laurel, declared once as a prelude (`JavaToLaurelCompiler.java:143-159`):

```
constrained int8  = x: int where x >= -128 & x <= 127 witness 0
constrained int16 = x: int where x >= -32768 & x <= 32767 witness 0
constrained int32 = x: int where x >= -2^31 & x <= 2^31-1 witness 0
constrained int64 = x: int where x >= -2^63 & x <= 2^63-1 witness 0
constrained char  = x: int where x >= 0 & x <= 65535 witness 0
constrained nat7 / nat15 / nat31 / nat63 / nat  (@Nat variants)
```

The Java→Laurel type mapping is in `translateType` (`JavaToLaurelCompiler.java:232-240`):
`INT → int32`, `SHORT → int16`, `BYTE → int8`, `LONG → int64`,
`CHAR → char`, `BOOLEAN → bool`. `@Nat` swaps to `natN`; `@Unbounded`
swaps to raw `int` / `nat`. `FLOAT`/`DOUBLE` throw
`"Unsupported type"` at line 239.

### Laurel cast primitives considered

- **`StmtExpr.AsType`**
  (`Strata/…/LaurelAST.lean:406-407`; Java factory
  `Laurel.asType(sr, target, typeName)` at
  `verifier/src/main/java/org/strata/jverify/laurel/Laurel.java`).
  Semantics: **type-only claim; value is unchanged.** Resolution
  (`Strata/…/Resolution.lean:2060-2093`) requires source and target to be
  in the same lineage. Constrained-type checks (the range assert) are
  triggered at *assignment / decl / return / param positions* by
  `ConstrainedTypeElim` (`Strata/…/ConstrainedTypeElim.lean:100-123`),
  **not by `AsType` itself**.

  **Rejected as the primary encoding** because Strata's
  `LaurelToCoreSchemaPass` throws `NotYetImplemented` on any `AsType` in
  expression position — only statement-boundary `AsType` (via a
  `varDecl(name: T := x)` lift) is currently lowered.

- **Helper-procedure calls** (what shipped).
  Emit each cast `(T) x` as a call `java?cast_to_T_from_S(x)` to a
  per-(source, target) helper procedure with a `requires` clause that
  encodes `T`'s range constraint. Uniform in every position
  (body, `precondition`, `postcondition`, return, if-condition) because
  Strata checks call preconditions wherever the call appears.

### Existing precedent for cast synthesis

`FloatingPointCastInserter` (`verifier/…/simplifications/FloatingPointCastInserter.java`)
extends `TreeTranslator` and rewrites the Java AST to insert explicit
`JCTypeCast` at flow boundaries (var-init, return, method arg, assign,
conditional branches, new-array elements, mixed binary operands) whenever
an integral type flows into a `FLOAT`/`DOUBLE`. Wired into `JavaLowerer.java:232, 304-310`
as `insertFloatingPointCasts`.

This is the template to mirror for integer casts if we want to make Java's
JLS §5.6 implicit promotion rules explicit before `JavaToLaurelCompiler`
looks at the tree.

### No truncation / modulo primitive in Laurel or JVerify

- `StmtExpr` (`Strata/…/LaurelAST.lean:370-472`) has no `Cast`, `Coerce`,
  `Truncate`, `Wrap`, or bitvector-modulo constructor.
- Bitvectors exist as `HighType.TBv` and `LiteralBv`, but there are no
  cast ops between `int` and `bv`.
- `convertConstantValue` at `JavaToLaurelCompiler.java:859-865` collapses
  `CHAR/INT/SHORT/BYTE/LONG` literals to a single `longLiteral` path
  (line 862); literal-level width distinctions are already lost.

## Java cast semantics recap (JLS §5.1.2, §5.1.3)

- **Widening** (`byte → short → int → long`, `char → int`, `char → long`):
  value-preserving. `char → int` is unsigned zero-extend.
- **Narrowing** (`long → int`, `int → short`, `int → byte`, `int → char`,
  `short → char`, `byte → char`, etc.): two's-complement truncation.
  - `(short) 100000  == -31072`
  - `(char) -1       == 65535`
  - `(byte) 300      == 44`
- **Reference casts** (`(Foo) x` on non-primitives): a separate concern;
  Laurel's `AsType` with a composite type name is the natural target.

## The three cases

### Case A — Same-signedness widening (`byte → short → int → long`)

Value is preserved. Target's constraint is trivially satisfied (source's
range ⊂ target's range). **A cast helper with the target's range as its
precondition trivially discharges.**

### Case B — `char → int / long` (unsigned zero-extend)

`char` already encoded as `[0, 65535]` on the `int` domain, so the
underlying integer value is already correct for the widened target.
**Same fix as Case A**: cast helper with the widened target's bounds.

### Case C — Narrowing

JLS mandates two's-complement truncation. Faithfully implementing that
would mean `(short) 100000 == -31072`, `(byte) 300 == 44`, `(char) -1 ==
65535`. Laurel has no truncation primitive, so faithful modeling would
require emitting a mod-based wrap formula in the frontend or adding a
new Laurel primitive.

**We are choosing not to model wrap semantics faithfully — at least for
now.** This is a deliberate design decision, taken to keep behavior
coherent with how JVerify currently handles in-range primitive
arithmetic (see next section).

## The coherence argument: match the existing overflow behavior

JVerify today does **not** implement two's-complement wrap for
primitive arithmetic within a single type either. In the current
encoding:

- `int i = Integer.MAX_VALUE; i = i + 1;` raises an **assertion
  failure**, not a silent wrap to `Integer.MIN_VALUE`. The result `i +
  1` is `2^31`, which fails the `int32` constraint (`x <= 2^31-1`).
- The constrained-type check is triggered on assignment / decl / return
  / param positions by `ConstrainedTypeElim`
  (`Strata/…/ConstrainedTypeElim.lean:100-123`), producing the
  assertion failure.

So JVerify's *effective* semantics for in-type overflow is "prove it
doesn't overflow, or fail." Users writing `check(x + 1 > x)` on an
`int` are told the property doesn't hold (because at `Integer.MAX_VALUE`
it doesn't) — but not via `x + 1 == Integer.MIN_VALUE`; rather via a
range violation.

This is not JLS-conformant. It also isn't wrong: it flags a real class
of program bugs (arithmetic that can silently wrap) and matches the
verification-oriented posture of tools like Dafny.

**For narrowing casts we adopt the same posture:** the cast is
translated as an assertion that the source value already fits the
target's range. If the source falls outside the target's constraint,
verification fails at the cast site.

### Semantics of a narrowing cast under this design

`(intN) x` where `x : intM` and `M > N`:

- If the verifier can prove `intN$constraint(x)`, the cast succeeds
  and the value is unchanged (the underlying integer stays the same;
  only the carried type changes).
- If the verifier cannot prove it, the user gets an assertion failure
  at the cast site.

Concretely:

- `(short) 100000` — verification fails: `100000 > 32767`.
- `(short) 100`     — verification succeeds: value is `100`.
- `(byte)  300`     — verification fails: `300 > 127`.
- `(char)  -1`      — verification fails: `-1 < 0`.
- `(int)   longX`   — verification succeeds iff `longX` provably lies
                       in `[-2^31, 2^31-1]`.

**Both widening and narrowing collapse to a single case:** emit a
call to a helper procedure whose `requires` clause encodes the target's
range. For widening the requires is trivially satisfied by the caller's
type; for narrowing it is the real proof obligation.

### Why this is coherent, not just easy

- It matches the existing in-type overflow behavior. A user who sees
  `int i = Integer.MAX_VALUE; i + 1;` fail with "range violation"
  will see `(short) 100000` fail the same way. No mental-model split.
- It matches the "prove absence of unsafe behavior" posture the rest
  of the tool takes: narrowing without knowing the source is in range
  is exactly the kind of latent bug a verifier should flag, not
  silently accept.
- It defers the harder (and z3-hostile) mod-based encoding until we
  have a real user need — at which point we can also revisit the
  in-type overflow story so both stay consistent.

### What we are giving up

- **JLS conformance.** Programs that intentionally rely on wrap
  semantics (`(short) (highBits & 0xFFFF)` idioms, `>>>` on `int`
  followed by `(byte)`, sign-extension code) will not verify without
  an assumption. Users can `assume(x >= Short.MIN_VALUE && x <=
  Short.MAX_VALUE)` at the cast site if they know it holds, mirroring
  what they'd do today for in-type overflow.
- **Symmetric treatment of `char`.** `(char) -1 == 65535` is a Java
  idiom (unsigned re-interpretation). Under this design it's a
  verification failure. Same escape hatch: `assume(x >= 0 && x <=
  65535)`.
- **A path to faithful modeling.** We're not building the mod formula,
  so if we later decide to model wrap, we're starting from zero. The
  helper `emitTwosComplementNarrow` in the earlier sketch is not
  implemented; that's the seed for later escalation.

## Later escalation paths (out of scope for the initial CR)

If real code teaches us that "prove no narrowing overflow" is too
strict:

- **Option 1 (mod desugaring).** Emit the wrap formula in the frontend.
  Sound but z3-hostile.
- **Option 2 (Laurel primitive).** Add `StmtExpr.NarrowCast` to Strata
  with truncation semantics, lowered by a new pass. Cleaner and
  keeps the surface Laurel readable, at the cost of an upstream Strata
  change and AST regen.
- **Bitvectors.** Move some or all of `int8/16/32/64` to SMT-Lib
  bitvector theory. Bigger shift; revisit when `--overflow-checks`
  grows real users.

In all three cases we should revisit the in-type overflow policy at
the same time so cast and arithmetic stay coherent.

## Recommendation

**Narrow-and-assert-fit for the initial CR**: every primitive cast
(widening or narrowing) is emitted as a call to a per-(source, target)
helper procedure whose `requires` clause encodes the target's range.
Add a companion `ImplicitCastInserter` frontend pass to make Java's
implicit JLS §5.6 primitive promotions explicit before
`JavaToLaurelCompiler` sees them.

Why the helper-procedure encoding over `AsType`:
- Strata's `LaurelToCoreSchemaPass` throws `NotYetImplemented` on
  expression-position `AsType`; only statement-boundary `AsType`
  (via a `varDecl(name: T := x)` lift) is lowered today. An
  `AsType`-based encoding would need every cast lifted to a
  statement boundary — an earlier iteration of this design did that,
  but the lift is (a) unsound for nested narrowing casts, and (b)
  invisible in contract expressions because Strata's
  `ConstrainedTypeElim` doesn't descend into `Requires`/`Ensures`
  bodies.
- Call preconditions are already checked in every position Strata
  looks at (body, requires, ensures, invariants, loop conditions),
  so a helper call gives uniform behavior "for free". The
  `postcondition((int res) -> res == (short) x)` tests, which the
  cast-to-temp lift silently accepted, now fail with the expected
  postcondition-does-not-hold diagnostic.
- No Strata change; no AST regen; no upstream/Brazil coordination.

Why narrow-and-assert-fit at all (versus modeling wrap):
- Coherent with the existing in-type overflow behavior (`i + 1`
  where `i == Integer.MAX_VALUE` already produces an assertion
  failure, not a silent wrap).
- Defers the JLS-wrap discussion until there's a user need — at
  which point we'd also revisit in-type overflow to keep the two
  stories consistent.

## Implementation sketch

### `convertExpression` extension

Add to the expression switch:

```java
case JCTree.JCTypeCast cast -> convertTypeCast(cast, renames);
```

`convertTypeCast` builds the helper name from source and target types
and emits a call:

```java
private StmtExpr convertTypeCast(JCTree.JCTypeCast cast, Map<String,String> renames) {
    var sr = toSourceRange(cast);
    var operand = convertExpression(cast.expr, renames);
    var targetType = translateType(cast.type);
    var sourceType = translateType(cast.expr.type);
    var targetName = ((LaurelType.CompositeType) targetType).name();
    var sourceName = laurelTypeName(sourceType);
    var helperName = "java?cast_to_" + targetName + "_from_" + sourceName;
    registerCastHelper(helperName, sourceName, sourceType, targetName);
    return call(sr, identifier(sr, helperName), List.of(operand));
}
```

`registerCastHelper` emits (once per pair) an opaque procedure:

```
procedure java?cast_to_int16_from_int32(x: int32): int16
  requires x >= -32768  summary "cast from int32 to int16"
  requires x <= 32767   summary "cast from int32 to int16"
  opaque(ensures $result == x)
```

Notes:
- Bounds are inlined (not `int16$constraint(x)`) because that
  constraint helper is generated by `ConstrainedTypeElim`, which
  runs *after* the initial resolve — so a call to it in frontend
  emission would fail resolution.
- The `summary` string is a Laurel `ErrorSummary`; Strata's
  Verifier renders it as `<summary> does not hold`, giving the
  cast-specific diagnostic
  `Error: cast from int32 to int16 does not hold` instead of the
  generic `precondition does not hold`.
- Reference casts are **not** handled by this path; they still hit
  the earlier reference-cast logic. Only casts whose target
  translates to a `CompositeType` (i.e. `int8/16/32/64/char/nat*`)
  go through `convertTypeCast`; a cast to any other target throws
  `"Cast to <T> is not yet supported"`.

Helpers are stored in a per-file `LinkedHashMap<String, Procedure>`
and emitted at file scope before the user procedures that call them.

### New pass: `ImplicitCastInserter`

Mirrors `FloatingPointCastInserter`. Extends `TreeTranslator`; walks the
Java tree and inserts explicit `JCTypeCast` nodes at the JLS §5.6
positions where the compiler applies implicit primitive conversion:

- variable initializer whose static type differs from the declared type
- method call argument whose static type differs from the formal
- assignment target vs. RHS type
- return statement vs. declared return type
- conditional (`? :`) branches whose types differ
- compound assignment (`byte b; b += 1;` — the implicit `(byte)`)
- binary operand promotion (`short + short → int`)
- array store when element type differs

The inserted cast targets the *base* primitive type
(`targetType.baseType()`), stripping `@Nat`/`@Unbounded`
annotations, so the inserted cast doesn't double-check the outer
statement's boundary constraint. Wired into `JavaLowerer.java` as
`insertImplicitCasts`, running after `insertFloatingPointCasts`.

The point of this pass is to centralize JLS §5.6 promotion rules in one
place so `JavaToLaurelCompiler` only has to handle explicit
`JCTypeCast` nodes.

## Testing

Un-skip and extend:

- `Operators.java:161-170` — `castNumTrivial`, `castNumInvalid`.
  Expected diagnostic on `castNumInvalid` is now `Error: cast from
  <S> to <T> does not hold` at the cast site.

Positive tests (`PrimitiveCasts.java`):

1. Every widening pair (`byte→short/int/long`, `short→int/long`,
   `int→long`, `char→int/long`) verifies with no diagnostic —
   target's requires is trivially satisfied.
2. Every narrowing pair with an in-range source
   (`int→byte/short/char`, `long→int/short/byte`, `short→byte`)
   verifies clean:
   ```java
   int i = 100;
   short s = (short) i;
   check(s == 100);
   ```
3. Every narrowing pair with an out-of-range source — expected
   diagnostic at the cast site:
   ```java
   int i = 100000;
   short s = (short) i;
//           ^^^^^^^^^ Error: cast from int32 to int16 does not hold
   ```
4. Narrowing with an `assume` — the escape hatch works:
   ```java
   int i;
   assume(i >= Short.MIN_VALUE && i <= Short.MAX_VALUE);
   short s = (short) i;
   check(s == i);
   ```
5. Chained casts: `(byte)(int) someLong` — one diagnostic per hop.
6. Binary promotion made explicit by `ImplicitCastInserter`.
7. Compound assignment: `byte b = 1; b += 1;` — implicit `(byte)`
   on the assign requires proving `b + 1 <= 127`.
8. Casts in **contract positions** — `precondition(...)`,
   `postcondition(...)`, if-condition, return. These work uniformly
   because a call's `requires` is checked in every position Strata
   examines.

## Open design points

1. **`@Nat` and `@Unbounded` interaction.** Casts from `@Unbounded int`
   to `int32` produce a `java?cast_to_int32_from_int` helper with the
   `int32` bounds as its requires. Casts from `int32` to
   `@Unbounded int` produce a `java?cast_to_int_from_int32` helper with
   no bounds (widening; trivially discharged).

2. **Float/double.** `translateType` still throws on `FLOAT`/`DOUBLE`;
   casts involving them fail at the type-translation step. Not this
   change's problem — resolved once `translateType` supports them.

3. **Documenting the choice for users.** `library/`'s public docs
   should note that narrowing primitive casts require the source to
   provably fit the target — same as arithmetic overflow. Add to the
   `@Pure`/`@Invariant` overview page.

4. **Postcondition-cast + matching-precondition case.** A method whose
   *own* precondition would prove the cast's precondition doesn't
   currently verify — the precondition isn't threaded into the call
   context when Strata evaluates the postcondition. See
   `PrimitiveCasts.postconditionCastVerifies` (kept as an
   aspirational test with a comment).

## Follow-up (out of scope for the initial CR)

- **Fold precondition context into postcondition-cast checks.** The
  aspirational case above. Likely needs a Strata-side change to how
  the postcondition VC is built.
- **Revisit the coherence policy** if JLS-conformant wrap becomes
  necessary for real workloads. Options in order of increasing
  complexity: mod desugaring in the frontend, a Laurel `NarrowCast`
  primitive, bitvector encoding. Any change here should also revisit
  in-type overflow so the two stay consistent.
- **Support `float`/`double` casts** — separate task; needs
  `translateType` to stop throwing on `FLOAT`/`DOUBLE` first.
- **Wider unsigned support.** Java has no unsigned integer types
  beyond `char`, but users occasionally reinterpret via masks. If we
  want to support the common `int & 0xFF` idiom cleanly, that's a
  separate follow-up.
- **Contract-lambda casts** (`postcondition((IntPredicate) r -> r
  == (short) x)`). The reference cast on the lambda itself is
  rejected by the frontend independent of the inner primitive cast;
  tracked separately.
