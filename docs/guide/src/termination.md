# Termination

Programs that contain loops or recursive calls may run indefinitely. Usually, such behavior is undesired.

By default, JVerify tries to prove that methods terminate, and emits an error if it can't. Even if a program terminates, JVerify might need you input to help prove that it does. In case you do not want to, you can turn off termination checking.

This page further explains how to prove termination using JVerify.

## Decreases clauses

JVerify needs a decreases clause for each method and loop. If none is specified explicitly, JVerify will guess one. A decreases clause contains an expression. JVerify proves termination by verifying that this expression:

1. Gets smaller with each recursive call or loop iteration
2. Has a lower bound that will eventually be reached

## Example with Recursion

```java
int factorial(@Nat int n) {
    decreases(n);
    
    if (n == 0) {
        return 1;
    } else {
        return n * factorial(n - 1);
    }
}
```

In this example, the `decreases(n)` clause tells JVerify that the value of `n` decreases with each recursive call. Since `n` is annotated with `@Nat`, it can't go below 0, ensuring the recursion will terminate.

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

Here, the lexicographic tuples ensure termination:
- For the call from `methodA(x)` to `methodB(x)`, the tuple `(x, 0)` is smaller than `(x, 1)`
- For the call from `methodB(x)` to `methodA(x-1)`, the tuple `(x-1, 1)` is smaller than `(x, 0)`

## Non-terminating Code

In some cases, you might intentionally write code that doesn't terminate (like a server loop). JVerify allows you to specify this with `decreases()`:

```java
void serverLoop() {
    decreases();

    while (true) {
        // Process requests indefinitely
        processNextRequest();
    }
}
```

The `decreases()` notation tells JVerify to skip the termination proof for this method. This can be used either for programs that do not terminate, or for programs where you do not want to put in the effort of proving termination.

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
