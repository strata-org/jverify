# Abstract contracts
When working with abstract methods, you might not yet be sure what the contract of the method will be. For this situation,  you can define abstract contracts using the method `isAbstract`. Here's an example where an abstract contract is used:

```java
@Contract(IntPredicate.class)
class IntPredicateContract implements IntPredicate {
    @Pure
    public boolean test(int value) {
        // Having the following line is different from not calling 'precondition' at all,
        // because that would mean there is a precondition, equivalent to 'precondition(true)'
        precondition(isAbstract());
        throw new ContractException();
    }
}
```

When working with a method with an abstract contract, we can use `preconditionOf` to refer to the contract. `preconditionOf` takes a method call and returns that call's precondition. Here's an example:
```java
boolean callPredicateWithFortyTwo(IntPredicate intPredicate) {
    precondition(preconditionOf(intPredicate.test(42)));
    // Without the precondition on the previous line, 
    // we would not have been allowed to make the following call. 
    return intPredicate.test(42);
}

void instantiateThePredicate() {
    var a = callPredicateWithFortyTwo((int i) -> { 
        precondition(i == 42);
        return true;
    });
    var b = callPredicateWithFortyTwo((int i) -> {
//                                   ^ Error: a precondition for this call could not be proved
        precondition(i == 10);
        return true;
    });
}
```