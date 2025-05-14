# Loop Invariants

JVerify verifies the behavior of code with loops through the use of a _loop invariant_. Since JVerify does not know how often a loop iterates, it needs to work with information that is true for _any_ iteration. The loop invariant is a condition that must be true every time the loop guard is evaluated, so when the loop is entered, and after each execution of the body.

The loop invariant can be used both to know what is true inside the loop, useful to prove the absence of exceptions inside it, and to learn about the state of the program when the loop exits.

Here follows the binary search example from the last section, but with some loop invariants added. Using the invariants, JVerify no longer emits an error about the array access inside the loop. 

```java
class BinarySearch {
    int binarySearch(int[] arr, int target) {
        var lo = 1;
        var hi = arr.length;

        while (lo < hi) {
            invariant(0 <= lo);
            invariant(lo <= hi);
            invariant(hi <= arr.length);

            var mid = lo + (hi - lo) / 2;
            if (key < arr[mid]) {
                hi = mid;
            } else if (arr[mid] < key) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        return -1;
    }
}
```

If we would now introduce a bug, for example by replacing
```java
var hi = arr.length;
```
with 
```java
var hi = arr.length - 1;
```

Then JVerify emits the error:
```
error: this invariant could not be proved on entry
 | invariant(lo <= hi);
             ^^^^^^^^ 
```

In this section, we've used invariants to prove that no uncaught exceptions occur inside the loop.

In the next sections, [Pre- and post-conditions](pre_and_postconditions.md), [Code types](code_types.md), [Forall and exists](forall_and_exists.md), we will introduce the tools needed to prove the correctness of the above `binarySearch` example, and we will use invariants also to prove facts that hold after the loop exits.