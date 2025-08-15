# Code types
In our previous binary search example, we had the following precondition:
```java
// The following precondition states that the input arr must be sorted.
precondition(forall((Integer i, Integer j) ->
  implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j])));
```

Instead of the comment, let's extract the expression into a method so it's self-documenting:

```java
@Pure
@Erased
boolean sorted(int[] arr)
{
  reads(arr);
  return forall((Integer i, Integer j) -> 
    implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j]));
}
```

Note that this uses three new concepts, `@Pure`, `@Erased` and `reads`. These are needed for JVerify to accept the code. The following two sub-sections discuss why we need these concepts and how they work.

## Pure code
We say code is pure if it does not have side effects and is deterministic (meaning it produces the same result if provided with the same inputs).

JVerify requires that expressions that occur in a nested context are pure. Nested contexts are:
- Arguments to method calls
- Expressions that are part of statements, such as the guards of `if` and `loop` statements. Return statements are the exception: their expression may be not pure.

The rules for pure expressions are as follows:
- Only methods annotated with `@Pure` can be called.
- Assignment expressions are not allowed
- `new` can only be used on immutable types (like records) 

The rules for pure blocks are:
- The last statement of the block must be either a return or an if-else statement.
- Any statements before the last one and after the contract, must be variable declarations with an initializer.

Summary table:

| | **@Pure code** | **impure code** |
|---|----------------|-----------------|
| **Can be called inside a contract** | yes            | no              |
| **Restricted use of statements** | yes            | no              |
| **Can update variables** | no             | yes             |

## Erased code
Since Java 5.0, it has had a feature called generics, which enables the programmer to give the type system additional information, through which it can find bugs it otherwise would not be able to. However, to prevent impacting runtime performance, the Java compiler removes any usage of the generics feature during compilation: a process called _type erasure_.

JVerify does something similar but for code that is used for verification. During compilation, JVerify removes any code for which it knows this is used only for verification. In many cases JVerify automatically determines what code should be erased, but in some cases it requires you, the programmer, to annotate code with `@Erased` to ensure that this will not be executed.

In addition to improving performance, code that is erased is allowed to use JVerify specific constructs that help with verification, but have no way of being executed, such as evaluating `forall` and `exists` expressions, and working with unbounded primitive numbers.

In an earlier example of a binary search program, we saw that the method `sorted` was given the annotation `@Erased`. Because it had this annotation it was allowed to call the `forall` method.

Here are the rules for different code contexts:
In the next section, [Quantifiers](quantifiers.md), we'll explain how the `forall` method works, as well as introduce `exists`.
| **Effect \ Context**                       | **@Erased method** | **executed method** |
|--------------------------------------------|--------------------|---------------------|
| **Can be called from an executed context** | no                 | yes                 |
| **Can use forall and exists**              | yes                | no                  |
| **Can use @Unbounded**                     | yes                | no                  |

Code inside a contract is always erased.

In the next section, [Quantifiers](quantifiers.md), we'll explain how the `forall` method works, as well as introduce `exists`. In the later section [Integers](integers.md), we'll talk more about the `@Unbounded` annotation.

