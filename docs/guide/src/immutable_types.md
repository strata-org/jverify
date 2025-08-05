# Immutable types

JVerify divides all types into two categories: mutable and immutable. Compared to immutable types, mutable ones have these properties:
- Can use reference equality `==`
- Can occur in a modifies clause
- Can _not_ be instantiated in a pure context

Immutable types are more reusable than mutable ones, since they can be instantiated in pure contexts. Mutable types are useful when they are used to improve performance.

Records are always immutable and classes are mutable with exceptions. Interfaces are immutable by default, but can be made mutable by annotating them with `@Modifiable`.

A mutable type may inherit from an immutable one. This may sound strange, but it only means that the mutable type will have an immutable part. An immutable type can not inherit from a mutable one.

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