# Interfaces & Abstract Classes

In one of the earliest sections, [Pre- and post-conditions](pre_and_postconditions.md), you've seen that method calls to the JVerify library can be placed in the body of a method to define the contract for that method. However, this approach does not work for methods without a body, such as a those in interfaces and abstract methods. For those cases, JVerify requires that you to define a contract class, which is a class that inherits from the interface or abstract class, and provides contracts for methods where it is still missing. Example:

```java
@Modifiable
interface I {
    int foo(int x);

    @Contract
    class IContract implements I {

        @Pure
        public int foo(int x) {
            precondition(x != 0);
            postcondition((Integer r) -> r > 10);
            throw new ContractException();
        }
    }
    
    default int bar(int x) {
        postcondition((Integer r) -> r > 10);
        precondition(x >= 0);
        return x + 10;
    }
}
```

Notice that for `bar`, the contract is provided by the interface `I`, while for `foo`, the contract is provided by the contract class `IContract`. Annotations on the type, such as `@Modifiable`, must be placed on the non-contract type, while annotations on the method, such as `@Pure`, must be placed on the method that defines the contract.

## Libraries without contracts

You will often come across already libraries for which no contracts were defined. To call a method from such a library from verified code, you will have to define its contract. You can do this using the `@Contract` annotation, similar to how you would for interfaces or abstract classes. Example:

```java
// JVerify provides a contract for `List` out of the box,
// but if it didn't, this is who we could add one.
@Contract
@Modifiable
abstract class ListContract<T> implements List<T> {
    @Override
    public int size() {
        postcondition((Integer r) -> r >= 0);
        throw new ContractException();
    }
}
```

We only wanted to provide a contract for `size`, so we made ListContract an _abstract_ class. This way, the Java compiler is satisfied even though we did not implement all methods from the interface `List<T>`.

Contrary to the `interface I` example from before, because `List<T>` is not defined in source, annotations such as `@Modifiable` are taken from the contract class.
