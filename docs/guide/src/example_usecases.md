# Example use-cases
WIP 

## Prevent exceptions

### UserProfile
```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/UserProfile.java}}
```

### Class cast example

### Numeric overflow

### Unchecked exceptions

## Prevent functional bugs

### AWS IAM
- deny before approve
- ...

### Pricing agreements
- Discount monotonicity
- Strategic irrelevance
- Gapless ranges (like consecutive date ranges)
- Values that satisfy a domain contraint (percentages)

## Safely optimize performance

### Binary search
```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/BinarySearch.java}}
```

### Diff two trees
(taken from a customer)

If you traverse both trees and for each node, check whether it is equal to the node at the same path in the other tree, you can determine which paths were added in each tree, but you will do a recursive equality comparison for each tree node, leading to `O(N^2)` complexity.

### Efficient string matching from customer Y