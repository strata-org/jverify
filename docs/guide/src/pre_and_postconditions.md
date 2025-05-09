# Pre- and post-conditions

Besides detecting exceptions, JVerify allows you to precisely state what a method does, and check that it actually does that. We can prove that the binary search method shown in the previous section behaves as intended by giving it a contract, which is done using calls to `precondition` and `postcondition`.

Here’s the updated example:

```java
class BinarySearch {    
  int binarySearch(int[] arr, int target) {
    // This postcondition guarantees that the method behaves as desired
    postcondition((Integer r) -> sequence(arr).contains(target)
      ? 0 <= r && r < arr.length && arr[r] == target
      : r == -1
    );

    // We need to add the following precondition, because otherwise we won't be able to prove the
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

If we now change anything in the implementation of `binarySearch` that changes its meaning, JVerify will detect that the implementation no longer matches the specification, and emit an error.

We had to add the call to `precondition` to be able to prove the `postcondition`, but this also has the desired effect of preventing us from incorrectly calling `binarySearch`. The method only behaves as intended if called with a sorted list, and this is now checked by JVerify, as you can see here:

```java
  void callBinarySearch() {
    binarySearch(new int[] {4, 1, 5}, 1);
//              ^ error: could not prove precondition
    binarySearch(new int[] {1, 3, 5}, 1); // no error
  }  
```

In the next section, [Code purposes](code_purposes.md), we'll reflect on what we've seen so far by introducing terminology that allows us to talk about the different usages of code when working with JVerify.