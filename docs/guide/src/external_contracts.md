# Adding contracts to third party code

In JVerify, when a verified method calls another method, then a contract must be defined for the callee. If the callee is owned by you, and has a body (as opposed to an abstract or interface method), then it is simplest to define its contract in its body.

If the callee is third-party code though, and does not have a contract yet, you will have to add a contract outside the callee body, using a contract class.

Here's an example of how to add a contract to the `intValue` and `add` methods of the existing BigInteger class:

```java
@Contract(BigInteger.class)
@Immutable
class BigIntegerContract  {
    @Unbounded int value;

    @Pure
    int intValue() {
        precondition(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE);
        postcondition((Integer b) -> b == value);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract add(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.value == this.value + v.value);
        throw new ContractException();
    }

}
```

Note the field `value`, which is not part of the `BigInteger` type. All fields that are part of contract classes are _erased_, meaning they do not occur at runtime, but can be used as part of contracts. The _erased_ concept is further explained in the subsection `Erased` in the section [Code types](code_types.md). 