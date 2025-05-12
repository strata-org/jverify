# Pre- and post-conditions

Besides detecting exceptions, JVerify allows you to precisely state what a method does, and check that it actually does that. We can prove that the binary search method shown in the previous section behaves as intended by giving it a contract, which is done using calls to `precondition` and `postcondition`.

Here’s the updated example. Note that this example contains calls to several JVerify utility methods that we have not seen before: `sequence`, `contains`, `drop` and `take`. 

`sequence` converts an array to a JVerify type `Sequence` which is similar to an immutable Java `List`, and is useful for verification. On a `Sequence` we can call `contains` which behaves as you'd expect. `drop` and `take` create a `Sequence` from an array, with elements selected as you'd expect.

Lastly, we're calling the JVerify method `forall`. We'll cover how this works in the section [Quantifiers](quantifiers.md) later on. In the below example, the comments explain what it means there.

```java
class BinarySearch {    
  int binarySearch(int[] arr, int target) {
    // This postcondition guarantees that the method behaves as desired
    postcondition((Integer r) -> sequence(arr).contains(target)
      ? 0 <= r && r < arr.length && arr[r] == target
      : r == -1
    );

    // The following precondition states that the input arr must be sorted.
    // Without this, we won't be able to prove the two new loop invariants, 
    // which are needed to prove the postcondition.
    // The method `forall` is one we'll get back to in the section Quantifiers
    precondition(forall((Integer i, Integer j) ->
            implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j])));
    
    // implementation
    var left = 0;
    var right = arr.length - 1;
    
    while (left < right) 
    {
      invariant(0 <= left);
      invariant(left <= right);
      invariant(right <= arr.length);
      invariant(!drop(arr, left).contains(target)); // added
      invariant(!take(arr, right).contains(target)); // added

      var mid = (left + right) / 2;
      if (arr[mid] == target) {
          // we can prove sequence(arr).contains(target)
          // and 0 <= mid && mid < arr.length && arr[mid] == target
          return mid;
      }
      if (arr[mid] < target) {
          left = mid + 1; 
          // we use the preconditon to prove the 4th invariant
      } else {
          right = mid; 
          // we use the precondition to prove the 5th invariant
      }
    }
    // we use the 4th and 5th invariant to prove that !sequence(arr).contains(target)
    return -1;
  }
}
```

If we now change anything in the implementation of `binarySearch` that would change its meaning, JVerify detects that the implementation no longer matches the specification and emits an error.

We had to add the call to `precondition` to be able to prove the `postcondition`, but this also has the desired effect of preventing us from incorrectly calling `binarySearch`. The method only behaves as intended if called with a sorted list, and this is now checked by JVerify, as you can see here:

```java
  void callBinarySearch() {
    binarySearch(new int[] {4, 1, 5}, 1);
//              ^ error: could not prove precondition
    binarySearch(new int[] {1, 3, 5}, 1); // no error
  }  
```

## Improvements
Our binary search method has the following precondition:

```java
// The following precondition states that the input arr must be sorted.
precondition(forall((Integer i, Integer j) ->
  implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j])));
```

Instead of the comment, let's extract this precondition into a method so it's self-documenting:

```java
@Pure
@Erased
boolean sorted(int[] arr)
{
  reads(arr);
  return forall((Integer i, Integer j) -> 
    implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j]));
}
```

Note that this uses three new concepts, `@Pure`, `@Erased` and `reads`. These are needed for JVerify to accept the code. In the next section, [Code types](code_types.md), we'll explain how these work.

