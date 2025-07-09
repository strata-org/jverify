# Common use-cases

## Prevent exceptions
- Like userprofile

## Prevent functional bugs
- Gapless ranges (like consecutive date ranges)
- Values that satisfy a domain contraint (percentages)

## Safely optimize performance

### Binary search

### Diff two trees
(taking from a customer)

If you traverse both trees and for each node, check whether it is equal to the node at the same path in the other tree, you can determine which paths were added in each tree, but you will do a recursive equality comparison for each tree node, leading to `O(N^2)` complexity.