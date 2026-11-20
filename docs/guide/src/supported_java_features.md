# Supported Java features

JVerify intends to support the full Java language, but is actively under development. It is built on [Strata](https://github.com/strata-org/Strata) and currently supports a limited subset of Java. Support is being expanded incrementally.

## What happens to code JVerify cannot handle

A construct outside the supported subset is reported in one of two ways:

- As an **error**, when it occurs in a static method or is one of the constructs listed
  under [Rejected up front](#rejected-up-front-rather-than-skipped). The run stops with
  exit code 2 and verifies nothing.
- As a **warning**, when it occurs in an instance method. That method is skipped and the
  rest of the code is verified.

**A skipped method is not verified, yet the run still prints `Found 0 errors` and exits
with code 0.** For example, `ratio` below uses `double`, which is not supported:

```java
import static org.strata.jverify.JVerify.*;
import org.strata.jverify.*;

class Skip {
    double ratio(double x) {
        return x * 0.5;
    }
    int ok(int x) {
        precondition(x < 100);
        postcondition((int r) -> r == x + 1);
        return x + 1;
    }
}
```

```text
Skip.java(5:28-5:29): warning: instance method 'ratio' was not verified: Unsupported type: double
Found 0 errors
Verified methods: 2
Failed methods: 0
Skipped methods: 1
```

So do not judge a run by `Found 0 errors` or by the exit code alone. A run is a pass only
when it reports both `Failed methods: 0` and `Skipped methods: 0`. A method that you opted
out yourself with `@Verify(false)` is also counted under `Skipped methods`, without a
warning; every other skipped method has a `was not verified` warning that names the
construct responsible.

## Currently supported

- **Static and instance methods.** Instance methods are translated, including calls
  through a receiver. A method that cannot be translated is skipped with a warning
  naming the construct responsible, not silently. A skipped method is not verified even
  though the run exits with code 0, as described above.
- **Primitive types:** `byte`, `short`, `int`, `long`, `char`, `boolean`.
- **Arithmetic, comparison, logical operators.**
- **Increment and decrement** (`++`, `--`), prefix and postfix, on a local, a parameter,
  a field or an array element.
- **Compound assignment** (`+=`, `-=`, `*=`, `/=`, `%=`) on a local, a parameter, a field
  or an array element. See the caveat below on narrowing.
- **Ternary expressions.**
- **Control flow:** `if`/`else`, `while`, `do`/`while`, `for`, `break`, `continue`,
  `return`, `assert`, variable declarations, blocks. A labelled `break` or `continue`
  works in simple nested loops, but some shapes are still rejected. A loop with no exit,
  such as `while (true) { }`, is rejected.
- **`switch` statements and `switch` expressions** in arrow form (`case 0 -> ...`). A
  statement arm may be a block. Not yet: `break` inside an arrow arm, and for `switch`
  expressions the statement-group form (`case 0:` with `yield`), a block or `throw` as
  an arm body, and `case` patterns.
- **JVerify contract primitives:** `precondition()`, `postcondition()`, `check()`,
  `assume()`, `implies()`, `invariant()`, `modifies()`, `old()`, `throwsOn()` and
  `everything()`.
  - `modifies(o)` states which objects a method may change; `modifies(everything())`
    allows any object to change.
  - `old(e)` in a postcondition is the value of `e` when the method was entered, for
    example `postcondition(() -> count == old(count) + 1)`.
- **Quantifiers:** `forall()`, `exists()`.
- **Transparent methods** via `@Transparent`. The Strata back-end does not yet accept a
  loop, a heap write or a `throw` in a transparent body, and reports it.
- **Static method calls.**
- **Arrays of primitives:** one-dimensional `int[]`, `long[]`, `short[]`, `byte[]`,
  `char[]` and `boolean[]`. This covers `new int[n]`, initializers such as `{1, 2, 3}`,
  `length`, element reads and writes, and arrays as parameters, results and fields.
  Every index is checked to be in bounds. Not yet: arrays of objects (this includes
  `String[]`), multi-dimensional arrays, `float[]` and `double[]`, `@Nullable` arrays,
  the enhanced `for` loop over an array, `clone()`, `System.arraycopy` and
  `java.util.Arrays` methods.
- **Constructors** of a class that is top-level or a static nested class and is not
  generic. The constructor body is verified, and a caller of `new Box(5)` may rely on
  the constructor's postcondition. Not yet: constructors of generic classes, inner
  (non-static nested), local and anonymous classes, enums and records. Such a
  constructor is not translated, so the fields of the new object are unconstrained
  immediately after construction and an assertion about them fails.
- **Exceptions:** `throws` clauses (unchecked types included; multi-`throws`
  joins to the closest common ancestor), `throw`, `try`/`catch`/`finally`
  (multi-catch included), exceptions that carry data set by their constructor, and
  exceptional behavior cases via `throwsOn(...).postcondition(...).modifies(...)` — see
  the [Exceptions](./exceptions.md) chapter. An unchecked exception that may leave a
  method must be declared in its `throws` clause unless `@AllowUncheckedExceptions`
  applies. Not yet: try-with-resources, and throwing an exception class of the Java
  standard library such as `IllegalArgumentException`; declare your own exception class
  instead.
- **Classes and interfaces**, with dynamic dispatch on the receiver's runtime type.
  An override must refine what it overrides, and JVerify checks that obligation, so a
  caller may reason from the static type's contract.
- **Generics:** a method's own type parameters and a class's. A type parameter carries
  no bound into the translation, so a body that uses the bound's members is still
  refused, and `==` on a type parameter is restricted as it is on `Object`.
- **Method overloading**, including overloads split across an inheritance chain.
- **Lambdas passed as arguments**, for a functional interface whose method is not
  generic. A lambda is checked as an override of the interface method: it assumes
  that method's precondition and must establish its postcondition, and the
  assertions in its body are checked. A captured variable is unconstrained inside
  the body. A lambda the translation cannot handle is reported as not verified.
- **Reference operations:** upcasts, downcasts, and `instanceof` on types declared in
  the code under verification.
- **Static fields**, including enum constants and `static` initializer blocks. A static
  field holds an arbitrary value when a method starts, like an instance field: a method
  that needs a fact about it states it as a precondition. The first use of a class in a
  method (a `new`, a static field access, or a static method call) may run the class's
  static initializer, and the translation follows the JVM rules for when it runs: the
  class that declares the member is initialized, its superclass first. On the
  Cortex route (interpret mode) the initializer runs at the first use, and an exception that
  escapes it reaches the use as an `ExceptionInInitializerError`, after
  which every use of the class throws `NoClassDefFoundError`; on the Laurel pipeline a
  method learns nothing from the initializer, and an initializer that may let an
  exception escape is refused like a constructor, and so is each method that may run it.
  A method whose use of a class may run an initializer that is not verified
  (`@Verify(false)`) or could not be translated is refused with that reason. Not yet: a
  static field of a library class is read with an unknown value, and the Laurel pipeline
  keeps it unchanged across library calls; a static field access inside a specification
  or a condition is refused when it may run an initializer.
  See the caveat below on narrowing.
- **Wrapper types** (`Integer`, `Long`, …): boxing and unboxing of a value held in a
  local, a parameter or a method result, and storing a box in a field. Not yet: `==`
  and `!=` on two boxes, unboxing or `equals` on a box read from a field or an array,
  and boxing inside a contract.
- **`String.length()` and `String.isEmpty()`**, in method bodies and in contracts. Not
  yet: `charAt` and string concatenation.
- **Annotations.** The JVerify library declares the annotations below.
  - Used by the verifier: `@Transparent`, `@Impure`, `@Verify`, `@Contract`,
    `@EmptyContract`, `@Unbounded`, `@Nat`, `@AllowUncheckedExceptions`.
  - Used by other modes and tools, not by verification: `@InterpretEntry` marks an entry
    point for `--interpret`; `@AsProperty` and `@CheckPreconditionsAtRuntime` are
    for testing and runtime checking of contracts.
  - Accepted but without effect on verification today: `@Erased`, `@Nullable`,
    `@Invariant`, `@InheritContract`. See the list below.

## Not yet supported

Major gaps being actively worked on or planned:

- `float` and `double` — a floating-point literal is rejected outright.
- Arrays of objects, multi-dimensional arrays, and the other array gaps listed above.
- `null`: the `null` literal is rejected, so `@Nullable` has no use yet.
- Records, beyond being treated as values: a component accessor is not emitted.
- `@Invariant` is accepted but the invariant is not checked. `@InheritContract` is
  accepted but adds nothing to the check that an override refines what it overrides.
  `@Erased` is accepted and ignored by the verifier.
- `decreases()` and `reads()` are accepted but not checked. In particular termination
  is not checked: a method that calls itself forever verifies.
- `sequence()` is rejected.
- Lambdas beyond contract lambdas and lambdas passed as arguments: a lambda stored in a
  variable, a method reference, and a lambda for a generic functional interface
- A narrowing compound assignment such as `byte b = 100; b += 100;` reports an
  assertion failure rather than Java's wrap-around result — the narrowing is not
  modelled, by design, and the diagnostic is currently misleading.
- Java standard library support (collections, `BigInteger`, streams, optionals). The
  shipped contracts cover some `java.lang` types; the generic collection contracts do
  not yet resolve.

If you encounter something not in either list, please open a [GitHub issue](https://github.com/strata-org/jverify/issues).

### Typical Java that is rejected today

These are the constructs most likely to stop a first attempt on existing code, with the
message JVerify prints for each:

- **`public static void main(String[] args)`**, and any other array of objects:
  `arrays of reference types are not yet supported`. Put `@Verify(false)` on `main` to
  verify the rest of the class.
- **The `null` literal**, as in `x == null` or `return null`:
  `Unsupported expression: NullLiteralExpr`.
- **A call on a receiver whose type is an interface of the Java standard library**,
  such as `List.get` or `Function.apply`:
  `call to 'get' on a receiver whose static type is not a class is not yet supported`.
  A call through an interface declared in the code under verification is supported.
- **Throwing a standard exception**, as in `throw new IllegalArgumentException("...")`:
  `Unsupported thrown expression type: IllegalArgumentException`.
- **`float` and `double`**: `Unsupported type: double`.
- **The enhanced `for` loop over an array**: `array access through a local variable with
  no recorded type (for example an enhanced for loop) is not yet supported`.
- **A loop with no exit**, such as `while (true) { }`: a message starting with
  `unsupported control flow`.

In a static method each of these is an error and the run exits with code 2. In an
instance method it is a warning and the method is skipped.

### Rejected up front, rather than skipped

Some unsupported constructs are reported as an **error** when JVerify reads your
source, rather than as a per-method "was not verified" warning. An error means a
non-zero exit, so a project that previously built green while quietly skipping the
affected methods will now fail. Rejecting up front is deliberate: a skipped method
looks verified at a glance, and these constructs cannot be checked at all.

The constructs rejected this way are:

- Bitwise and shift operators (`&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`)
- `%` on `float` or `double`
- `==` and `!=` on references where neither operand's type is impure — including boxed
  comparisons such as `Integer == Integer`
- Instance initializer blocks
- `isAbstract()` anywhere but a precondition
- In a constructor, a contract call (`precondition`, `postcondition`, …) that comes
  neither immediately after the call to `super` or `this` nor at the end of the body.
  A misplaced contract call in any other method follows the rule of the last item in
  this list.
- A postcondition or `modifies` clause on a `@Transparent` method
- A malformed `@Contract` class
- Any construct that cannot be translated in a **static** method: a static method that
  cannot be translated is an error, an instance method a warning. The warning does not
  change the exit code: the instance method is skipped, not verified, and the run still
  prints `Found 0 errors` and exits with code 0.

To keep building while one of these is present, opt the code out of verification
with `@Verify(false)`. It is honored on the method and on any enclosing class.
