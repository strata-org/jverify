# Testing unverified contracts

If proving that a JVerify method matches its specification is difficult, then you can choose to instead test it using property-based testing. This is generally more compute heavy, and is therefore best suited for pieces of code near the bottom of the call-stack. Although it does not require a proof, it does require that the programmer specifies how to generate inputs for the method to test. JVerify supports the generator annotations from the Java property based testing tool [jqwik](https://jqwik.net/).

## Marking a contract for testing

Annotate the method whose contract you want to test with `@AsProperty`, and annotate its parameters with jqwik's `@ForAll` so that jqwik knows to generate values for them. The method's pre- and postconditions are written exactly as they are for verification.

```java
import org.strata.jverify.AsProperty;
import net.jqwik.api.ForAll;

import static org.strata.jverify.JVerify.*;

@AsProperty
int binarySearch(@ForAll int[] arr, @ForAll int target) {
    precondition(sorted(arr));
    postcondition((Integer r) -> sequence(arr).contains(target)
        ? 0 <= r && r < arr.length && arr[r] == target
        : r == -1);

    throw new RuntimeException("not yet implemented");
}
```

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

If the input declares a class `Foo`, the generated output declares a class `FooGenerated` that `extends Foo`, so the generated tests can call the contract methods directly. The tool processes one file at a time; wiring it into a larger Gradle pipeline that runs over a whole source set is left to the consumer's build.

## Worked example

Assuming the `binarySearch` method above lives in a class `BinarySearchProperty`, running the tool on that file produces a jqwik property like:

```java
package org.strata.jverify.examples;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
// ... (the original imports are carried forward)

public class BinarySearchPropertyGenerated extends BinarySearchProperty {

    @Property
    public boolean binarySearchProperty(@ForAll int[] arr, @ForAll int target) {
        Assume.that(sorted(arr));
        int r = binarySearch(arr, target);
        return (sequence(arr).contains(target)
                ? 0 <= r && r < arr.length && arr[r] == target
                : r == -1);
    }
}
```

jqwik then generates inputs for `arr` and `target`, discards the ones that fail the `Assume.that(...)` precondition, and checks that the postcondition holds for the rest.

## Translation rules

| Contract construct | Generated jqwik code |
|---|---|
| `precondition(P)` | `Assume.that(P);` |
| `postcondition((T r) -> Q)` | `T r = method(args); return Q;` |
| `postcondition(boolExpr)` | `method(args); return boolExpr;` |
| Multiple `postcondition(...)` calls in one method | Conjoined with `&&`, each clause parenthesised to preserve precedence |
| `old(e)` where `e` refers only to the method's parameters | Snapshotted before the call into a pre-state temporary (`var __old0 = e;`) and the `old(e)` reference is replaced by that temporary |
| Frame clauses `reads(...)` / `modifies(...)` | Dropped — they constrain the verifier, not runtime behaviour |
| Quantifiers `forall(...)` / `exists(...)` | Skipped with a warning — they range over infinite domains and have no runtime semantics |
| `old(e)` where `e` refers to a local declared inside the contract body | Skipped with a warning — the captured expression would not be in scope in the generated test |

The translation is otherwise verbatim: clauses are emitted as written, except that each conjunct is parenthesised when several postconditions are joined with `&&`.

### Time-traveling with `old(...)`

`old(e)` lets a postcondition refer to the pre-call value of an expression. The tool makes this executable by snapshotting `e` into a temporary before the method runs, then substituting that temporary back into the postcondition. For example:

```java
@AsProperty
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

### Frame clauses

`reads(...)` and `modifies(...)` describe what the verifier may assume about memory effects. They have no runtime meaning, so the tool drops them from the generated property; they neither appear in the output nor block translation.

Warnings for skipped clauses are printed to stderr so you can see exactly what was and wasn't translated.
