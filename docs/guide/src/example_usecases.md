# Example use-cases
This section contains a number of small code examples that hopefully give you an idea of what types of bugs you can prevent by using JVerify.

#### Prevent dereferencing null
Without instructions, JVerify will detect many common types of bugs. Here's some code that shows two `NullPointerException`s being detected by JVerify:

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/NullCheck.java}}
```

#### Keep a cache up to date
We might store some data that is an aggregate of other data. With JVerify, we can check that the aggregate remains up to date. Here's an example:

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/SumCache.java}}
```

#### Validate runtime inputs
Here's an example that uses the annotation `@CheckPreconditionsAtRuntime` to let contracts from JVerify be checked at runtime, which is useful when we're working with external data. In this example, we receive a list of ranges, and want to make sure the range are consecutive, and each individual range ends after it starts.

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/ValidRanges.java}}
```

#### Prove partial program properties
In the following example, we're computing a discounted price. We don't know exactly what the discounted value should be, but we do want to make sure that the discounted price is at least 30% of the original price. 

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/PositivePrice.java}}
```

#### Prove complete program properties
Sometimes we have programs whose entire desired behavior can be specified with a property that's much simpler than the program itself. Here follows an example of a `findIndex` method that is implemented using binary search. Its desired behavior, to find the index of an element in an array if it exists, is specified in two lines, but the algorithm to compute that result is much more complicated.

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/BinarySearch.java}}
```

#### Safely optimize code
Sometimes we have an algorithm for computing some data, but there's a different algorithm that computes it more quickly, at the cost of being more complex. JVerify allows you to prove that the more compliated algorithm behaves the same as the slower once. Here's an example where that is done for the fibonacci sequence: 

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/Fibonacci.java}}
```

