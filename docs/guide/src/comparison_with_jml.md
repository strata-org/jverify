### Comparison of JVerify and JML

#### Syntax

#### Semantics

JML introduces datatypes, which look like this:
```java
//@ model datatype LinkedList<T> { Nil, Cons(T head, List<T> tail) }
```

While defining an equivaklent construct in JVerify requires users to use Java's sealed keyword, like this:
```java
sealed interface LinkedList<T> {}
record Cons<T>(T head, LinkedList<T> tail) implements LinkedList<T> {}
record Nil() implements LinkedList<T> {}
```

JML also plans on having match expressions and statements


#### Purity
JML differentiates between four times of purity:
- Pure: may allocate newly allocated objects. When a pure method is called in a specification, reference equality may not be called on its result, unless it can be proven that the result is non-fresh.
- Spec pure: same restrictions as pure and the result must be deterministic. May do allocation as long as they are not returned.
- Strictly pure: does not modify the heap.
- No state: does not interact with the heap