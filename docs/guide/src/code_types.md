# Code types
All code in a Java program that uses JVerify can be assigned two properties: whether it is pure, and whether it is erased. The following two sections discuss what these properties are for and how they work.

## Pure code

Code that occurs in a contract, such as the arguments of calls to `precondition`, `postcondition` and `check`, must follow the rules for pure code. These are:
- Only methods annotated with `@Pure` can be called.
- Local variables or fields can not be updated.

Code that occurs in a method annotated with `@Pure` must follow those same rules, and additionally:
- Only the following statements can be used:
  - variable declaration statements,
  - calls to `precondition`, `postcondition`, `reads` and `assert`
  - a single `return <expr>` statement at the end, in which other pure methods may be called
- If any fields of a reference are read, that reference must occur in a `reads` call. We'll explain what reads calls are in the next section. 

Summary table:

| | **@Pure method** | **impure method** |
|---|---|---|
| **Can be called inside a contract** | yes | no |
| **Restricted use of statements** | yes | no |
| **Needs reads calls** | yes | no |
| **Can update variables** | no | yes |

### Reads calls

JVerify guarantees that a `@Pure` method is deterministic, meaning that given the same  it returns the same output. To use this property however, we need to know what the input is. In a language with references like Java, it's not enough to know what the method arguments are. 

JVerify requires that a method marked with `@Pure` is explicit about which objects it reads fields from. This can be specified using `reads` calls. The `reads` method takes one or multiple `object` arguments. Example:

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

In an earlier example of a binary search program, we saw that a Java method can be given the annotation `@Erased`. This annotation change the rules that are applied to the code inside that method, and change where the method may be called.

Any code inside a method annotated with `@Erased`, or that is part of a contract, is _erased_, meaning it will not be executed at runtime. Erased code is used purely for writing JVerify specifications and proofs. Because it is not executed, it may perform operations that would be costly to execute, such as evaluating `forall` and `exists` expressions, and working with unbounded numbers.

Here are the rules for different code contexts:

| **Effect \ Context**                       | **@Erased method** | **executed method** |
|--------------------------------------------|--------------------|---------------------|
| **Can be called from an executed context** | no                 | yes                 |
| **Can use forall and exists**              | yes                | no                  |
| **Can use @Unbounded**                     | yes                | no                  |

Code inside a contract is always erased.

In the next section, [Quantifiers](quantifiers.md), we'll explain how the `forall` method works, as well an introduce `exists.

