# Specification, proof and implementation

This section does not introduce any features, but it defines terminology that we commonly use to talk about Java programs using JVerify. When writing Java code that uses JVerify, each piece of code serves one or several of the following goals:

- Implementation, the Java code that will actually execute.
- Specification, used to specify the behavior of a piece of implementation. Will not be executed.
- Proof, used to verify that an implementation adheres to a specification and that it does not throw unchecked exceptions. Should not be executed.

Code can be used for multiple goals, although often it is used for just one. If we look at the previous `binarySearch` example, shown again below, the method `sorted` and the calls to `precondition`, `postcondition` and `reads`, are part of the specification, while the calls to `invariant` are part of the proof. All the other code is implementation.

```java
class BinarySearch {
  int binarySearch(int[] arr, int target) {
      
    // specification
    precondition(sorted(arr));
    postcondition((Integer r) -> sequence(arr).contains(target)
      ? 0 <= r && r < arr.length && arr[r] == target
      : r == -1
    );

    // implementation
    var left = 0;
    var right = arr.length - 1;
    
    while (left < right) 
    {
      // start proof
      invariant(0 <= left);
      invariant(left <= right);
      invariant(right <= arr.length);
      invariant(!drop(arr, left).contains(target));
      invariant(!take(arr, right).contains(target));
      // end proof
      
      var mid = (left + right) / 2;
      if (arr[mid] == target) {
          return mid;
      }
      if (arr[mid] < target) {
          left = mid + 1;
      } else {
          right = mid;
      }
    }
    return -1;
  }

  // specification
  @Pure
  @Erased
  boolean sorted(int[] arr)
  { 
    reads(arr);
    return forall((Integer i, Integer j) -> 
            implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j]));
  }
}
```
