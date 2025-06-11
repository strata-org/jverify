# JML Tests

This directory contains a set of tests that were extracted from the [OpenJML](https://github.com/OpenJML/OpenJML/) project and translated into JVerify syntax. These tests can be used as examples of how to migrate JML specifications into JVerify. The user guide below provides more examples and explanations.

# JML to JVerify Translation Guide

This guide explains how to translate Java Modeling Language (JML) specifications into JVerify syntax. JML is a behavioral interface specification language that can be used to specify the behavior of Java modules. JVerify is Amazon's formal verification tool for Java that allows similar specification capabilities with a different syntax.

## Introduction

JML specifications are typically written as special comments in Java code, while JVerify uses method calls and annotations. This guide will help you convert your existing JML specifications to JVerify.

## Basic Translations

| JML Syntax | JVerify Syntax                            | Description |
|------------|-------------------------------------------|-------------|
| `//@ requires P` | `precondition(P);`                        | Method precondition |
| `//@ ensures P` | `postcondition(P);`                       | Method postcondition |
| `//@ assert P` | `check(P);`                               | Assertion |
| `\old(v)` | `old(v)`                                  | Value of variable before method execution |
| `//@ assignable E` | `modifies(E);`                            | Frame condition (what can be modified) |
| `\fresh(v)` | `fresh(v)`                                | Object was newly allocated |
| `//@ maintaining P` | `invariant(P);`                           | Loop invariant |
| `//@ invariant P` | `@Invariant @Pure` method                 | Class invariant |
| `\result` | Lambda parameter in postcondition         | Return value of method |
| `//@ pure` | `@Pure` annotation + `reads(this)`        | Method with no side effects |
| `//@ model` method | `@Erased` annotation                      | Specification-only method |
| `\forall int i; P(i); Q(i)` | `forall((Integer i) -> !P(i) \|\| Q(i));` | Universal quantification |

## Detailed Examples

### Class Invariants

In JML:
```java
//@ public invariant balance >= 0;
//@ public invariant accountNumber > 0;
```

In JVerify:
```java
@Invariant @Pure
private boolean checkValid() {
    reads(this);
    return balance >= 0 && accountNumber > 0;
}
```

### Method Preconditions and Postconditions

In JML:
```java
//@ requires initialBalance >= 0;
//@ requires accNum > 0;
//@ ensures balance == initialBalance;
//@ ensures accountNumber == accNum;
public BankAccount(int initialBalance, int accNum) {
    ...
```

In JVerify:
```java
public BankAccount(int initialBalance, int accNum) {
    precondition(initialBalance >= 0);
    precondition(accNum > 0);
    postcondition(balance == initialBalance);
    postcondition(accountNumber == accNum);
    ...
```

### Working with Return Values

In JML, the special variable `\result` refers to the return value:
```java
//@ ensures \result == balance;
public int deposit(int amount) {
    ...
```

In JVerify, use a lambda expression in the postcondition:
```java
public int deposit(int amount) {
    postcondition((Integer r) -> r == balance);
    ...
```

### Old Values

In JML:
```java
//@ ensures balance == \old(balance) + amount;
```

In JVerify:
```java
postcondition(balance == old(balance) + amount);
```

### Pure Methods

In JML:
```java
//@ pure
public int getBalance() {
    return balance;
}
```

In JVerify:
```java
@Pure
public int getBalance() {
    reads(this);
    return balance;
}
```

### Frame Conditions (Assignable)

In JML:
```java
//@ assignable isActive;
```

In JVerify:
```java
modifies(this);  // Currently JVerify cannot specify only a field of a class
```

### Model Methods

In JML:
```java
//@ public model boolean isValidAccount() {
//@     return accountNumber > 0 && balance >= 0;
//@ }
```

In JVerify:
```java
@Erased
@Pure
private boolean isValidAccount() {
    reads(this);
    return accountNumber > 0 && balance >= 0;
}
```

### Quantified Expressions

In JML:
```java
//@ requires (\forall int i; 0 <= i && i < transactions.length; transactions[i] >= 0);
```

In JVerify:
```java
precondition(forall((Integer i) -> !(0 <= i && i < transactions.length) || transactions[i] >= 0));
```

### Loop Invariants

In JML:
```java
//@ maintaining 0 <= i && i <= n;
for (int i = 0; i < n - 1; i++) {
        ...
```

In JVerify:
```java
for (int i = 0; i < n - 1; i++) {
    invariant(0 <= i && i <= n);
    ...
```

## Advanced Patterns

### Summation

JML has built-in support for summation with `\sum`. In JVerify, you can model this using a ghost method:

```java
@Erased
@Pure
public static int mSum(int[] arr, int start, int end) {
    precondition(start >= 0 && start <= end && end <= arr.length);
    decreases(end-start);
    return (start == end ? 0 : arr[start] + mSum(arr, start+1, end));
}

// Then use it in specifications:
postcondition((Integer result) -> result == mSum(transactions, 0, transactions.length));
```


## JML Features Without Direct JVerify Equivalents

Some JML features don't have direct equivalents in JVerify and require workarounds or alternative approaches:

1. **Fine-grained assignable clauses**: JML allows specifying individual fields in `assignable` clauses, while JVerify's `modifies` currently only works at the object level.
   ```java
   // JML: Fine-grained control
   //@ assignable balance;  // Only balance can be modified
   
   // JVerify: Object-level granularity
   modifies(this);  // The entire object can be modified
   ```

2. **Signals clauses**: JML has `signals` clauses to specify exceptional behavior. JVerify doesn't have a direct equivalent and requires custom handling.
   ```java
   // JML
   //@ signals (Exception e) balance == \old(balance);
   
   // JVerify: No direct equivalent, needs custom handling
   ```

3. **Model fields**: JML allows model fields that represent abstract state. In JVerify, you need to use methods with `@Erased` annotation instead.

4. **Multiple Method Behaviors**: JML allows specifying different behaviors for a method based on different preconditions. JVerify doesn't have a direct equivalent for this feature.
   ```java
   // JML
   /*@ public normal_behavior
     @   requires amount > 0 && amount <= balance;
     @   ensures balance == \old(balance) - amount;
     @   ensures \result == true;
     @ also
     @ public normal_behavior
     @   requires amount <= 0 || amount > balance;
     @   ensures balance == \old(balance);
     @   ensures \result == false;
     @*/
   public boolean withdraw(int amount) {
       if (amount > 0 && amount <= balance) {
           balance -= amount;
           return true;
       }
       return false;
   }
   
   // JVerify: Need to combine all behaviors into a single specification
   public boolean withdraw(int amount) {
       // Combined precondition (true, since method handles all cases)
       // Combined postcondition using conditional expressions
       postcondition((Boolean result) -> 
           (amount > 0 && amount <= old(balance)) ? 
               (balance == old(balance) - amount && result == true) : 
               (balance == old(balance) && result == false)
       );
       
       if (amount > 0 && amount <= balance) {
           balance -= amount;
           return true;
       }
       return false;
   }
   ```

## Important Differences

1. JML uses special comments (`//@`), while JVerify uses method calls and annotations
2. JVerify requires explicit `reads` clauses in `@Pure` methods
3. JVerify uses lambda expressions for quantifiers and return values
4. Class invariants in JVerify are implemented as methods with `@Invariant` annotation
5. JVerify uses `@Erased` for specification-only methods instead of JML's `model` keyword

## Conclusion

This guide provides a starting point for translating JML specifications to JVerify. While the syntax differs, most JML concepts have direct equivalents in JVerify. For more complex specifications, you may need to create helper methods or adapt your approach to fit JVerify's capabilities.
