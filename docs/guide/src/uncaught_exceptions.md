# Common exceptions
JVerify allows you to provide a specification for a program, and prove that the program satisfies that. However, if even you do not provide a specification, JVerify can still help by detecting uncaught exceptions.

JVerify will emit an error if it thinks code might throw any of these common exceptions: `NullReferenceException`, `IndexOutOfBoundsException`, `ClassCastException`, `ArithmeticException`.

For example, JVerify emits an error for this program:
```java
class Foo {
    int divZero(int x) {
        if (x > 0) {
            return 10 / x; // no error
        }
        return 20 / x;
//              ^^^^^^ error: possible ArithmeticException
    }
}
```

While in the above cases, JVerify is correct in detecting that the program has a bug, there are more complex cases where the program is correct, but JVerify can not prove this without hints, so it emits an error. For example here:

```java
class BinarySearch {
  int binarySearch(int[] arr, int target) {
      var lo = 1;
      var hi = arr.length;

      while (lo < hi) {
          var mid = lo + (hi - lo) / 2;
          if (key < arr[mid]) {
//                  ^^^^^^^^ error: possible IndexOutOfBoundsException
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

In the following section, [Loop Invariants](loop_invariants.md), we learn how to prove that programs with loops do not throw uncaught exceptions. Later on, in the section [Working with null](working_with_null), we'll look at how to verify programs that work with `null` while proving no `NullReferenceException` can occur.