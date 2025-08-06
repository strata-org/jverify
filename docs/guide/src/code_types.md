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

JVerify requires that code that occurs in contracts, such as calls to `precondition`, `postcondition` and `check`, is pure.

Code is considered pure if it follows these rules:
- Only methods annotated with `@Pure` can be called.
- Local variables or fields can not be updated, so assignment operators such as `=` can not be used unless they're initializing the variable.
- `new` can only be used on immutable types (like records). More information is in the section [Immutable types](immutable_types.md). 

Code that occurs in a method annotated with `@Pure` must follow the rules for pure code, and additionally:
- Only the following statements can be used:
  - variable declaration statements,
  - calls to `precondition`, `postcondition`, `reads`, and `check`
  - a single `return <expr>` statement at the end, in which other pure methods may be called
- If any non-final fields of a reference are read, that reference must occur in a `reads` call. We'll explain what reads calls are below. 

Summary table:

| | **@Pure method** | **impure method** |
|---|---|---|
| **Can be called inside a contract** | yes | no |
| **Restricted use of statements** | yes | no |
| **Needs reads calls** | yes | no |
| **Can update variables** | no | yes |

### Reads calls

JVerify guarantees that a `@Pure` method is deterministic, meaning that given the same  it returns the same output. To use this property however, we need to know what the input is. In a language with references like Java, it's not enough to know what the method arguments are. 

JVerify requires that a method marked with `@Pure` is explicit about which objects it reads non-final fields from. This can be specified using `reads` calls. The `reads` method takes one or multiple `object` arguments. Example:

```java
class Engine {
    int horsePower;
}
class Car {
    Engine engine;
    
    public int readHorsePower() {
        reads(this, engine); // without this line, the next line would emit an error
        return this.engine.horsePower;
    }
}
``` 

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

