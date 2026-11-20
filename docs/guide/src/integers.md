# Integers
This section explain how prove properties about integers in Java code.

## Nat

The annotation `@Nat` can be used to tell JVerify that an integer is never negative. Example:

```java
int factorial(@Nat int n) {
    if (n == 0) {
        return 1;
    } else {
        return factorial(n - 1); // pass
    }
}
```

Without the `@Nat` annotation, JVerify would not have been able to prove termination, and indeed the program does not terminate if `n` is smaller than zero. Instead of `@Nat`, we could also have used `precondition(n >= 0)`, but that is more verbose.

## Bounded numbers
The types `byte`, `int`, `short` and `long` in Java are bounded. The values of these numbers all have lower and upper bounds, and doing arithmetic operations that cause these numbers to surpass these bounds, called an overflow, generally causes undesired results.

JVerify will check that any arithmetic operations on bounded numbers will not overflow. Each `+`, `-`, `*` and unary `-` on `int` or `long` is a proof obligation at the operation itself, so an overflow in an intermediate subexpression is reported even when the final result is back in range:

```java
int average(int a, int b) {
  precondition(0 <= a && 0 <= b);
  return (a + b) / 2; // error: no int overflow on '+' does not hold
}
```

You can let JVerify know no such overflow occurs by bounding the provided values. The bound itself is also checked, so write it in a form that cannot overflow — compare against a quotient or a difference instead of restating the product or sum:

```java
int triple(int x) {
  precondition(x <= Integer.MAX_VALUE / 3 && x >= Integer.MIN_VALUE / 3);
  return 3 * x; // pass
}

int average(int a, int b) {
  precondition(0 <= a && 0 <= b && a <= Integer.MAX_VALUE - b);
  return (a + b) / 2; // pass
}
```

Writing `precondition(a + b <= Integer.MAX_VALUE)` instead would be reported as an overflow in the precondition itself: specification arithmetic is checked the same way as implementation arithmetic. To state a fact over the mathematical (unbounded) values, use `@Unbounded` (next section).

Known gaps and costs:

- Division overflow (`Integer.MIN_VALUE / -1`, which wraps in Java) is not yet an obligation at the operation; it is still only caught when the result reaches an assignment, return or parameter.
- A specification lambda's own `@Unbounded` binder (`postcondition((@Unbounded int r) -> ...)`) is treated as bounded: the Java compiler drops the annotation from the generated lambda method. Bind the value to an `@Unbounded` local (or return it from an `@Unbounded` method) instead.
- In `--interpret` mode each checked operation is one extra procedure call, so arithmetic-heavy code uses roughly 3x more execution steps. The default step budget is 100000 per call; raise `--fuel` if a long run stops with "failed to terminate".

## Unbounded numbers
Using the annotation `@Unbounded`, you can annotate integer types so JVerify will allow them to have unbounded values. Example:
```java
@Erased
@Unbounded int triple(@Unbounded int x) {
  return 3 * x; // pass
}
```

The parameter is `@Unbounded` too: the operands decide whether an operation is checked, so `3 * x` on a bounded `int x` would still carry an int overflow obligation even in a method with an `@Unbounded` return type.

Note that the `@Unbounded` annotation can only be used in erased code. It is often useful in specifications, because working with unbounded numbers is easier, and since specifications are erased, they can use them.

## Combining the above
Here follows an example that uses unbounded numbers is its specification, bounded numbers in the implementation, and natural numbers in both. It shows how each of these concepts is useful:

```java
{{#include ../../../examples/src/test/java/org/strata/jverify/examples/Fibonacci.java}}
```