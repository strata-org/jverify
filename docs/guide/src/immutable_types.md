# Immutable types

JVerify divides all types into two categories: mutable and immutable. Compared to immutable types, mutable ones have additional features:
- Can use reference equality `==`
- Can occur in a modifies clause

However, a restriction of mutable types is that they can not be instantiated in a pure context.

Pure code is easier to use, since it can be used in contracts. Similarly, immutable types are easier to use, since they can be instantiated in contracts. Mutable types are useful when they are used to improve performance.

JVerify considers records to be immutable and classes to be mutable. An interface is immutable by default, but can be made mutable by annotating it with `@Modifiable`.

A mutable type may inherit from an immutable one, which only means that the mutable type has a part that is immutable. An immutable type however can not inherit from a mutable one.

Here is an example showing the rules for mutable types:

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/ImmutableTypes.java}}
```

### Object

In Java an important type is `java.lang.Object`. JVerify distinguishes between these two types of `Object`:
- `Object`: no support for `==`
- `@Modifiable Object`: supports `==`, inherits from `Object`

Records, immutable interfaces and type parameters always inherit from `Object`. Classes and mutable interfaces always inherit from `@Modifiable Object`.

Only `@Modifiable Object` can be instantiated directly, so `new Object` is always interpreted as `new @Modifiable Object`.

Here is an example showing the rules for Object types:

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/ObjectRules.java}}
```

### Immutable classes

We said before that classes are always mutable. However, classes from external libraries can be marked as immutable, but this must only be done if they behave as such. Examples of immutable classes are the boxed primitive classes such as `Integer` and `Double`.