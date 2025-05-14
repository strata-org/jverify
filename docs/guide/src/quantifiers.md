# Quantifiers
JVerify introduces a special kind of expression called a quantifier. There are two types of quantifiers, `forall` and `exists`. Both take a lambda that can take any number of arguments of any type. The `forall` states that for all inputs the lambda could possibly take, the lambda body must be true, and the `exists` states that there must exist an input to the lambda for which the body is true.

Given a `forall` that ranges over types with infinitely many values, such as all integers, we can only ever determine if the forall holds by using logical reasoning. Similar, for an `exists` expression with an infinite domain, we can only prove that it is false by using logical reasoning. For these reasons, these types of expression do not occur in regular Java. `forall` and `exists` can only be used in an erased context, since these can not be executed at runtime.

You can use quantifiers to say something about the behavior of methods. For example, given an argument of type `Function`, we can say that this function only returns values greater than 10:
```java
void higherOrder(f: Function<Integer, Integer>) {
  precondition(forall((Integer i) -> f(i) > 10);
}
```

Or that a `List` only contains even values 
```java
void higherOrder(l: List<Integer>) {
  precondition(forall((Integer i) -> l.get(i) % 2 == 0);
}
```

In the next section, [Code purposes](code_purposes.md), we'll reflect on what we've seen so far by introducing terminology that allows us to talk about the different usages of code when working with JVerify.
