# Pre- and post-conditions

Besides detecting exceptions, JVerify allows you to precisely state what a method does, and check that it actually does that. While the binary search method from before would be correct after fixing the bug, it only works as intended if the array passed to it is sorted. We can guarantee that it works as intended by giving it a contract, which is done using calls to `precondition` and `postcondition`. Here’s the example from above but with those calls, and a fix for the bug:

```java
class BinarySearch {
  void callBinarySearch() {
    binarySearch(new int[] {4,1,5}, 1);
//              ^ error: could not prove precondition
    binarySearch(new int[] {1,3,5}, 1); // no error
  }  
    
  int binarySearch(int[] arr, int target) {
    // This postcondition guarantees that the method behaves as desired
    postcondition((Integer r) -> sequence(arr).contains(target)
      ? 0 <= r && r < arr.length && arr[r] == target
      : r == -1
    );

    // Without the following precondition, we won't be able to prove the
    // two new loop invariants, which are needed to prove the postcondition
    precondition(sorted(arr));
    
    // implementation
    var left = 0;
    var right = arr.length - 1;
    
    while (left < right) 
    {
      invariant(0 <= left);
      invariant(left <= right);
      invariant(right <= arr.length);
      invariant(!drop(arr, left).contains(target)); // needed for the postcondition
      invariant(!take(arr, right).contains(target)); // needed for the postcondition

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

  @Pure // enables calling sorted in a contract such as precondition
  @Erased // enables the call to forall in the body
  boolean sorted(int[] arr)
  { 
    reads(arr); // necessary to access arr in a @Pure method

    return forall((Integer i, Integer j) -> 
            implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j]));
  }
}
```

If we now change anything in the implementation of the program that changes its meaning, JVerify will detect that the implementation of `binarySearch` no longer matches its specification, and emit an error. 