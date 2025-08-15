# Using 'new' in pure code

JVerify divides all concrete types into two categories: mutable and immutable. Compared to immutable types, mutable ones have these properties:
- They can have non-final fields
- They can refer to `this` in reads and modifies clauses
- They can use reference equality `==`
- They _cannot_ be instantiated in a pure context

Immutable types are more reusable than mutable ones, since they can be instantiated in pure contexts. Mutable types are useful when they are used to improve performance, which their mutable fields and reference equality can enable.

Records are immutable while classes are mutable with an exception (see below).

Here is an example of mutable and immutable types:

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/ImmutableTypes.java}}
```

## Immutable classes

We said before that classes are mutable with an exception. The exception is that classes from external libraries can be marked as immutable, but this must only be done if they behave as such. Examples of immutable classes are the boxed primitive classes such as `Integer` and `Double`, and the `BigInteger` and `BigDecimal` class.

# Reference equality
By default, the reference equality operator `==` can not be used on interface types. To enable this, the interface must be marked with `@Reference`. However, a reference interface can not be implemented by records.

By default reference equality can not be used on the type `Object`. However, prefixing the `Object` type with `@Reference` enables using reference equality.

Conceptually, `Object` and `@Reference Object` define two different types, where `@Reference Object` inherits from `Object`. Records and non-reference interfaces inherit from `Object`, while classes and reference interfaces inherit from `@Reference Object`.

`new Object` always returns a `@Reference Object`, and can only be used in an impure context.

Here is an example showing the rules for Object types:

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/ObjectRules.java}}
```