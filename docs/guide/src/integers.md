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

JVerify will check that any arithmetic operations on bounded numbers will not overflow. Example:

```java
int triple(int x) {
  return 3 * x; // error: returned value may not be within the bounds of 'int'    
} 
```

You can let JVerify know no such overflow occurs by bounding the provided values:
```java
int triple(int x) {
  precondition(3 * x < Integer.MAX_VALUE);
  
  return 3 * x; // pass    
} 
```

## Unbounded numbers
Using the annotation `@Unbounded`, you can annotate integer types so JVerify will allow them to have unbounded values. Example:
```java
@Erased
@Unbounded int triple(int x) {
  return 3 * x; // pass    
}
```

Note that the `@Unbounded` annotation can only be used in erased code. It is often useful in specifications, because working with unbounded numbers is easier, and since specifications are erased, they can use them.

## Combining the above
Here follows an example that uses unbounded numbers is its specification, bounded numbers in the implementation, and natural numbers in both. It shows how each of these concepts is useful:

```java
{{#include ../../../examples/src/test/java/org/strata/jverify/examples/Fibonacci.java}}
```