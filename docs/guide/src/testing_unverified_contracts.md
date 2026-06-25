# Testing unverified contracts

If proving that a JVerify method matches its specification is difficult, then you can choose to instead test it using property-based testing. This is generally more compute heavy, and is therefore best suited for pieces of code near the bottom of the call-stack. Although it does not require a proof, it does require that the programmer specifies how to generate inputs for the method to test. JVerify supports the generator annotations from the Java property based testing tool [jqwik](https://jqwik.net/).

## Marking a contract for testing

Annotate the method whose contract you want to test with `@AsProperty`, and annotate its parameters with jqwik's `@ForAll` so that jqwik knows to generate values for them. The pre- and postconditions are written as method calls in the body.

```java
import org.strata.jverify.AsProperty;
import org.strata.jverify.Pure;
import net.jqwik.api.ForAll;

import static org.strata.jverify.JVerify.*;

@AsProperty
@Pure
int abs(@ForAll int x) {
    precondition(x != Integer.MIN_VALUE);
    postcondition((Integer r) -> r >= 0 && (r == x || r == -x));
    return x < 0 ? -x : x;
}
```

The postcondition is a genuine property — the result is non-negative *and* has the same magnitude as the input — not merely a restatement of the one-line body. The precondition excludes `Integer.MIN_VALUE`, whose negation overflows (`-Integer.MIN_VALUE == Integer.MIN_VALUE`, which is negative); without it the property would fail on exactly that input, which is the kind of edge case property-based testing is good at finding.

> **Contracts under test must be executable.** Unlike a contract you verify,
> a contract you *test* is actually run, so its pre- and postconditions must be
> ordinary executable Java. Verification-only constructs — `forall`, `exists`,
> `sequence`, and similar — have no runtime behaviour (they throw if executed),
> so the tool skips clauses containing `forall`/`exists` and you should express
> testable properties in plain Java instead.

> **Why `@Pure`?** A generated property test checks the postcondition but never
> the frame, so dropping a `modifies` clause is only provably sound when the
> method is `@Pure` or its frame is `modifies(everything())` (see
> [Frame clauses and soundness](#frame-clauses-and-soundness)). `abs` modifies
> nothing and has no loop, so we mark it `@Pure`; JVerify then guarantees it is
> side-effect-free, which is what makes testing its postcondition sound and
> keeps the translation warning-free.

## Generating the jqwik property

The `contracts2jqwik` tool translates an `@AsProperty` method into a jqwik `@Property` test. It is a source-to-source generator: you give it a `.java` file and it produces a sibling `.java` file containing the property tests, which you review and check in alongside the original.

Build the tool's runnable distribution from the JVerify repository root:

```
./gradlew :contracts2jqwik:installDist
```

Then run it on a file. With one argument it writes the generated source to stdout:

```
./contracts2jqwik/build/install/contracts2jqwik/bin/contracts2jqwik path/to/Foo.java
```

With a second argument it writes the generated source to disk:

```
./contracts2jqwik/build/install/contracts2jqwik/bin/contracts2jqwik path/to/Foo.java path/to/FooGenerated.java
```

If the input declares a class `Foo`, the generated output declares a class `FooGenerated` that `extends Foo`, so the generated tests can call the contract methods (and any `@Provide` generators) directly. The tool processes one file at a time; wiring it into a larger Gradle pipeline that runs over a whole source set is left to the consumer's build.

## Worked example

Assuming the `abs` method above lives in a class `AbsProperty`, running the tool on that file produces:

```java
package org.strata.jverify.examples;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
// ... (the original imports are carried forward)

public class AbsPropertyGenerated extends AbsProperty {

    @Property
    public boolean absProperty(@ForAll int x) {
        Assume.that(x != Integer.MIN_VALUE);
        int r = abs(x);
        return (r >= 0 && (r == x || r == -x));
    }
}
```

jqwik then generates values for `x`, discards the (single) value that fails the `Assume.that(...)` precondition, and checks that the postcondition holds for the rest.

## Advanced: expensive preconditions and custom generators

The `abs` precondition excludes just one value, so rejection sampling is free. But some preconditions are satisfied by almost none of the randomly-generated inputs. Consider a binary search, whose precondition is that the array is sorted:

```java
@AsProperty
int binarySearch(@ForAll int[] arr, @ForAll int target) {
    precondition(isSorted(arr));
    postcondition((Integer r) -> contains(arr, target)
        ? 0 <= r && r < arr.length && arr[r] == target
        : r == -1);
    // ... iterative binary search ...
}
```

A randomly-generated array is almost never sorted (the probability is `1/n!`), so a `@Property` that relied on `Assume.that(isSorted(arr))` would discard nearly every input. jqwik aborts a property whose discard ratio is too high, so this would not just be slow — it would fail to run at all.

The fix is to **generate valid inputs directly** rather than filter random ones. jqwik does this with a `@Provide` method referenced by name from `@ForAll`:

```java
@Provide
Arbitrary<int[]> sortedArrays() {
    return Arbitraries.integers().between(-50, 50)
        .array(int[].class).ofMaxSize(16)
        .map(a -> { java.util.Arrays.sort(a); return a; });   // generate, then sort
}
```

Annotate the parameter with the generator's name, `@ForAll("sortedArrays") int[] arr`; `contracts2jqwik` carries that annotation through to the generated `@Property`. Now every generated array is already sorted, the `Assume.that(isSorted(arr))` is always satisfied, and nothing is discarded.

### When a contract method cannot be `@Pure`

`binarySearch` uses a loop, so it cannot be `@Pure` (pure methods may not loop or reassign variables). It still modifies no objects, but `contracts2jqwik` cannot confirm that, so it translates the method and prints a frame soundness warning recording that the generated test does not check the frame (see [Frame clauses and soundness](#frame-clauses-and-soundness)). Since the method is frame-clean by inspection, the warning is the only signal you need — the property runs as usual:

```
contracts2jqwik path/to/BinarySearchProperty.java
```

## Translation rules

| Contract construct | Generated jqwik code |
|---|---|
| `precondition(P)` | `Assume.that(P);` |
| `postcondition((T r) -> Q)` | `T r = method(args); return Q;` |
| `postcondition(boolExpr)` | `method(args); return boolExpr;` |
| Multiple `postcondition(...)` calls in one method | Conjoined with `&&`, each clause parenthesised to preserve precedence |
| `@ForAll` / `@ForAll("provider")` on a parameter | Carried through verbatim, so custom `@Provide` generators keep working |
| `old(e)` where `e` refers only to the method's parameters | Snapshotted before the call into a pre-state temporary (`var __old0 = e;`) and the `old(e)` reference is replaced by that temporary |
| Frame clause `reads(...)` | Dropped — a verifier-only read effect with no runtime meaning |
| Frame clause `modifies(...)` | Always dropped, since the generated test cannot check it. If dropping it is provably sound (`@Pure`, or `modifies(everything())`) the method translates silently; otherwise it still translates but with a frame soundness warning. See [Frame clauses and soundness](#frame-clauses-and-soundness) |
| Quantifiers `forall(...)` / `exists(...)` | Skipped with a warning — they range over infinite domains and have no runtime semantics |
| `old(e)` where `e` refers to a local declared inside the contract body | Skipped with a warning — the captured expression would not be in scope in the generated test |

The translation is otherwise verbatim: clauses are emitted as written, except that each conjunct is parenthesised when several postconditions are joined with `&&`.

### Time-traveling with `old(...)`

`old(e)` lets a postcondition refer to the pre-call value of an expression. The tool makes this executable by snapshotting `e` into a temporary before the method runs, then substituting that temporary back into the postcondition. For example:

```java
@AsProperty
@Pure
public static int increment(@ForAll int n) {
    postcondition((Integer r) -> r == old(n) + 1);
    return n + 1;
}
```

generates:

```java
@Property
public boolean incrementProperty(@ForAll int n) {
    var __old0 = n;
    int r = increment(n);
    return (r == __old0 + 1);
}
```

This works as long as the expression inside `old(...)` refers only to the contract method's parameters. If it refers to a local declared inside the contract body (for example a receiver constructed there), the snapshot can't be formed in the generated test, so that clause is skipped with a warning rather than emitting broken code. Supporting `old(...)` over a constructed receiver requires generating the receiver too, which is not yet implemented.

### Frame clauses and soundness

A `modifies(...)` clause is a *promise* about which objects a method may mutate
(see [Reading and modifying objects](reads_and_modifies.md)). The verifier
checks that promise, but a generated property test only evaluates the
postcondition — it never observes which objects the method touched. So simply
dropping the `modifies` clause would be **unsound**: a method that mutates more
than its frame allows would still pass the generated test, even though
verification would reject it. Testing a contract is meant to be a weaker but
sound stand-in for verifying it, and silently ignoring the frame breaks that.

To make this explicit, `contracts2jqwik` always translates the method (the
frame is dropped either way), but it stays **silent** only when dropping the
frame loses nothing — that is, when **either**:

- the method is **`@Pure`** — JVerify independently guarantees a pure method
  modifies nothing, so there is no frame obligation left for the test to check; **or**
- its frame is exactly **`modifies(everything())`** — an unconstraining frame
  with nothing to violate.

For any other method it translates but emits a **frame soundness warning**:

- a non-`@Pure` method with a *narrower* `modifies(...)` clause, or
- a non-`@Pure` method with *no* `modifies` clause — JVerify's default frame is
  empty (the method may modify nothing), which is itself a promise the test
  cannot check.

The warning records that the generated test does not check the frame, so the
result is only guaranteed sound when the method is `@Pure` or its frame is
`modifies(everything())`. If a method genuinely mutates nothing, mark it
`@Pure` (as `abs` is above) to silence the warning. If it cannot be `@Pure` but
you can see it is frame-clean — like the looping `binarySearch` above — the
warning is just a reminder; the property still runs.

A fully sound treatment of arbitrary frames would require checking the
`modifies` clause at runtime (instrumenting each field write in the method, and
propagating each callee's frame into its caller's). That is not yet implemented;
the warning marks exactly where that gap remains.

`reads(...)` clauses, by contrast, constrain only what a `@Pure` method may
read and have no runtime effect, so they are simply dropped.

Warnings for skipped clauses and methods are printed to stderr so you can see
exactly what was and wasn't translated.
