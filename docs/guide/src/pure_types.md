# Pure types

JVerify divides all types into two categories: pure and impure. Compared to pure types, impure ones have these properties:
- They can have non-final fields
- They can use reference equality `==`
- They _cannot_ be instantiated in a pure context

Pure types are more reusable than mutable ones, since they can be instantiated in pure contexts. Impure types are useful when they are used to improve performance, which their mutable fields and reference equality can enable.

Records are always pure and classes are impure with an exception (see below). Interfaces are impure by default, but can be made pure by annotating them with `@Pure`. An impure type may inherit from a pure one. A pure type can not inherit from an impure one.

The following file shows how pure and impure types can be used, and their relation to immutable and mutable types.

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/PureAndImmutable.java}}
```

### Object

In Java an important type is `java.lang.Object`. JVerify distinguishes between these two types of `Object`:
- `Object`: no support for `==`
- `@Modifiable Object`: supports `==`, inherits from `Object`

Records, immutable interfaces and type parameters always inherit from `Object`. Classes and mutable interfaces always inherit from `@Modifiable Object`.

`Object` cannot be instantiated directly, so `new Object` is always interpreted as `new @Modifiable Object`.

Here is an example showing the rules for Object types:

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/ObjectRules.java}}
```

### Immutable classes

We said before that classes are mutable with an exception. The exception is that classes from external libraries can be marked as immutable, but this must only be done if they behave as such. Examples of immutable classes are the boxed primitive classes such as `Integer` and `Double`, and the `BigInteger` and `BigDecimal` class.