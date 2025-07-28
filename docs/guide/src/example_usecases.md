# Example use-cases
This section contains a number of small code examples that hopefully give you an idea of what types of bugs you can prevent by using JVerify.

The code examples make use of various JVerify features that, unless you've read other parts of this guide, you won't be familiar with. However, most of what you'll see is intuitive so we hope you can follow along. If not, please consult the rest of the guide.

#### Prevent dereferencing null
Without instructions, JVerify will detect many common types of bugs. Here's some code that shows two `NullPointerException`s being detected by JVerify:

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/NullCheck.java}}
```

#### Keep a cache up to date
We might store some data that is an aggregate of other data. With JVerify, we can check that the aggregate remains up to date. Here's an example:

WARNING: this example does not work yet due to missing contracts for the standard library.

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/SumCache.java}}
```

#### Validating runtime inputs
Here's an example that parses text into a list of `Range` objects. There is a constraint that needs to hold for each individual range, and a constraint on the list of range objects. Using the method `JVerify.callIfAble`, we can do a runtime check of JVerify contracts, which allows us to concisely enter the safe contractual world without having to repeat preconditions.

WARNING: this example does not work yet, since we don't support `JVerify.callIfAble` yet.

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/ProcessRangesIfAble.java}}
```

#### Safely interacting with reflective or generated code
Here's an example that uses the annotation `@CheckPreconditionsAtRuntime` to let contracts from JVerify be checked at runtime, which is useful when code that we don't control calls our verified code. 

In this example, we're using the Jackson library to deserialize JSON into a list of `Range` objects. The library is calling the constructor of `Range`. To make sure it's not constructing invalid `Range` objects, we've marked the record `Range` with `@CheckPreconditionsAtRuntime`, so its constructor checks whether its `@Invariant` holds at runtime.

WARNING: this example does not work yet, since `@CheckPreconditionsAtRuntime` is only available on methods, not on types.

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/ValidRangesJackson.java}}
```

If we wanted the above error handling to be more granular, like in the "Validating runtime inputs" example, then we could have defined a custom deserializer for Range that calls the default one but handles the `PreconditionFailure`.

When creating a verified library that will be called from unverified code, `@CheckPreconditionsAtRuntime` can be used to ensure calls are made correctly.

#### Prove implementation matches a much simpler specification
Here follows an example of a `findIndex` method that is implemented using binary search. Its desired behavior, to find the index of an element in an array if it exists, is specified in two lines, but the algorithm to compute that result is much more complicated.

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/BinarySearch.java}}
```

#### Prove properties of a specification
In many cases, you might be unsure that your specification does exactly what you want it to. Luckily, you can increase your confidence by proving properties of the specification.

In the following example, we're computing a discounted price, but we're worried about discounting too much. To increase our confidence, we write a separate proof that the discounted price is at least 30% of the original price.

WARNING: this example does not work yet due to missing contracts for the standard library.

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/MinimumPricePercentage.java}}
```

#### Safely optimize code
Sometimes we have an algorithm for computing some data, but there's a different algorithm that computes it faster, at the cost of being more complex. JVerify allows you to prove that the two algorithms return the same result, allowing you to safely use the fast one. Here's an example where that is done for the fibonacci sequence. `spec` is the slow but concise algorithm, while `implementation` is the fast one. 

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/Fibonacci.java}}
```
