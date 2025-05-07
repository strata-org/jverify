# Loop Invariants

JVerify has trouble understanding loops without help. To help it, the programmer must define _loop invariants_, which might also help the programmer better understand their program. A loop invariant is a condition that must be true every time the loop guard is evaluated, so right before the loop, and at the end of the loop body.

Here follows the binary search example from before, but with loop invariants. Using the invariants, JVerify emits an error that points us to the bug.

```java
class BinarySearch {
    int buggyBinarySearch(int[] arr, int target) {
        var left = 0;
        var right = arr.length - 1; // Bug: should be arr.Length

        while (left < right)
        {
            invariant(0 <= left);
            invariant(left <= right);
//                ^^^^^^^^^^^^^ error: this invariant could not be proved on entry
            invariant(right <= arr.length);

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
}
```

With the above error, “this invariant could not be proved on entry”, we know that the problem occurs before the loop and relates to the value of `left` and `right`, so it should be easy for us to see that we need to correct the initial assignment to right. If we had changed the assignment to left to `var left = -1;`, then the error on the second invariant would be resolved, but the first invariant would show an error, so we must update the assignment to `right`.