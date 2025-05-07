# Common exceptions

Without any additional input from the programmer, JVerify will emit an error if it thinks code might throw any of these common exceptions: `NullReferencePointer`, `IndexOutOfBoundsException`, `ClassCastException`, `ArithmeticException`.

For example, JVerify emits an error for this program:
```java
class Foo {
    void divZero() {
        var x = 10 / 0;
//              ^^^^^^ error: possible ArithmeticException
    }
}
```

and for the following, more complex program:

```java
class BinarySearch {
  int buggyBinarySearch(int[] arr, int target) {
      var left = 0;
      var right = arr.length - 1; // Bug: should be just arr.length
      
      while (left < right) 
      {
        var mid = (left + right) / 2;
        if (arr[mid] == target) {
//          ^^^^^^^^ error: possible IndexOutOfBoundsException
            return mid;
        }
        if (arr[mid] < target) {
            left = mid + 1;
        } else {
            right = mid - 1;
        }
      }
      return -1;
  }
}
```

In the section [Nullable](nullable.md), we'll look at how JVerify prevents NullReferenceExceptions. In the following section, [Loop Invariants](loop_invariants.md), we instruct JVerify so it can help find fix the bug in the above binary search program. 