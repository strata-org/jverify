# Code types
In our previous binary search example, we had the following precondition:
```java
// The following precondition states that the input arr must be sorted.
precondition(forall((Integer i, Integer j) ->
  implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j])));
```

Instead of the comment, let's extract the expression into a method so it's self-documenting:

```java
@Transparent
@Erased
boolean sorted(int[] arr)
{
  reads(arr);
  return forall((Integer i, Integer j) -> 
    implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j]));
}
```

Note that this uses three new concepts, `@Transparent`, `@Erased` and `reads`. The following two sub-sections discuss `@Transparent` and `@Erased`; `reads` is covered in [Reading and Modifying Objects](reads_and_modifies.md).

## Transparent code
A method annotated with `@Transparent` is compiled to a transparent Laurel procedure: its callers, including contracts, see its body rather than only its contract. In the example above, a caller that knows `sorted(arr)` holds also knows the quantified fact it returns.

The rules for `@Transparent` methods are:
- It may not declare postconditions or a `modifies` clause. A transparent body has no place for either; what the caller learns comes from the body itself.
- Everything else about the body is checked by the Strata back-end. Strata does not yet accept every statement in a transparent body; for example, loops, writes to the heap, and `throw` are currently rejected there, and Strata reports the offending construct.
- `@Transparent` is not inherited: an overriding method is transparent only if it is annotated itself.

Contracts may call methods without `@Transparent` as well, as long as the callee does not modify any existing objects. Such a call tells the contract only what the callee's own contract says.

Summary table:

| | **@Transparent method** | **other method** |
|---|-----------------|-----------------|
| **Can be called inside a contract** | yes | yes, if it modifies nothing |
| **Callers see its body** | yes | no, only its contract |
| **Can declare postconditions and `modifies`** | no | yes |

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

