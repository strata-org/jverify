# Termination

Programs that contain loops or recursive calls may run indefinitely. Usually, such behavior is undesired.

By default, JVerify tries to prove that methods terminate, and emits an error if it can't. Even if a program terminates, JVerify might need you input to help prove that it does. In case you do not want to, you can turn off termination checking.

This page further explains how to prove termination using JVerify.

## Decreases clauses

JVerify needs a decreases call for each method and loop. If none is specified explicitly, JVerify will guess one. A decreases call has one argument, called the termination metric. JVerify proves termination by verifying that the metric satisfies two rules:

1. Decreasing: the metric becomes smaller with each recursive call or loop iteration.
2. Has a lower bound: the metric only recurses/loops if at or above a lower bound. The value of the lower bound depends on the type of the expression. For `int`, the lower bound is 0.

## Example with Recursion

```java
int factorial(int n) {
    precondition(n >= 0);
    decreases(n);
    
    if (n == 0) {
        return 1;
    } else {
        return n * factorial(n - 1);
    }
}
```

In this example, the `decreases(n)` clause tells JVerify that the value of `n` is the termination metric. Since the recursive call uses `n - 1`, `n` decreases with each recursive call, the decreasing rule is satisfied. Since the recursive call is only done when `n` is greater than 0, the lower bound rule is also satisfied. With both rules satisfied, we have proven that the method terminates.

Suppose we had not added the precondition `precondition(n >= 0);`, then calling factorial with a negative number would cause it to run indefinitely. JVerify would report `decreases expression must be bounded below by 0`, because the recursive call could be done with a value lower than 0, violating the lower bound rule.

If we would then change the expression `n == 0` into `n <= 0`, then the lower bound rule would be satisfied again.

## Lexicographic Ordering

For more complex termination scenarios, you can provide multiple expressions in a decreases clause, creating a lexicographic ordering:

```java
void complexRecursion(int x, int y) {
    decreases(x, y);
    
    if (x > 0) {
        complexRecursion(x - 1, y + 10); // x decreases
    } else if (y > 0) {
        complexRecursion(x, y - 1); // y decreases
    }
}
```

JVerify checks that either the first component decreases, or it stays the same and the second component decreases.

## Mutual Recursion

Mutual recursion requires special attention for termination proofs. Consider two mutually recursive methods:

```java
void methodA(int x) {
    decreases(x, 1);
    
    if (x > 0) {
        methodB(x);
    }
}

void methodB(int x) {
    decreases(x, 0);
    
    if (x > 0) {
        methodA(x - 1);
    }
}
```

Here, the lexicographically ordered tuples ensure termination:
- For the call from `methodA(x)` to `methodB(x)`, the tuple `(x, 0)` is smaller than `(x, 1)`
- For the call from `methodB(x)` to `methodA(x-1)`, the tuple `(x-1, 1)` is smaller than `(x, 0)`

Note that if comparing tuples of different sizes, if the larger one starts with the same values as the smaller one, then the larger one is considered greater. A decreases expression of `x` is considered greater than `x, 0`, so in the above example of `methodA` we could have specified `decreases(x)` instead of `decreases(x,1)`.

In cases of mutual recursion, it can occur that a caller and callee use termination metrics with different types. In these cases, JVerify can not prove that one is greater than the other, so proving termination fails.

## Non-terminating Code

In some cases, you might intentionally write code that doesn't terminate (like a server loop). JVerify allows you to specify this with `mayNotTerminate()`:

```java
void serverLoop() {
    mayNotTerminate();

    while (true) {
        // Process requests indefinitely
        processNextRequest();
    }
}
```

The `mayNotTerminate()` notation tells JVerify to skip the termination proof for this method. This can be used either for programs that do not terminate, or for programs where you do not want to put in the effort of proving termination.

## Automatic Decreases Clauses

In many simple cases, JVerify can automatically infer an appropriate decreases clause, so you don't need to specify it explicitly. For example:

```java
int fibonacci(int n) {
    // JVerify automatically infers decreases(n)
    if (n < 2) {
        return n;
    } else {
        return fibonacci(n - 1) + fibonacci(n - 2);
    }
}
```

However, for complex recursion patterns or when JVerify cannot determine termination, you'll need to provide explicit decreases clauses.

### Integers

In the next section, [Integers](integers.md), we'll look at verifying code that uses integers, including the `@Nat` annotation which is often useful for termination proofs.