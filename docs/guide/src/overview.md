# JVerify

JVerify is a tool that at compile-time can detect almost every bug in a Java program. It uses computer-aided theorem proving to statically verify that executable Java code will always satisfy some user-provided specifications for all possible executions of the code.

Program specifications are provided by making calls to the JVerify library. These calls can be removed during compilation, using a plugin for `javac`, so they will not have an effect at run-time. Java code that contains JVerify specifications is still regular Java code, so it can be analyzed by any Java IDE.

## Example
Here’s example of a binary search method with a bug. Because the method lacks the right annotations, JVerify can not directly point us to the bug, although it does emit an error because it was not able to prove the absence of bugs:

```java
class BinarySearch {
  int buggyBinarySearch(int[] arr, int target) {
      var left = 0;
      var right = arr.length - 1; // Bug: should be just arr.length
      
      while (left < right) 
      {
        var mid = (left + right) / 2;
        if (arr[mid] == target) {
//          ^^^^^^^^ JVerify error: index out of range
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

### Invariants
When loops are involved, JVerify often needs the programmer to provide additional information about how the program works, which can be done using a loop invariant, specified using a call to the `invariant` method from the JVerify library. When we add invariants to the above program, JVerify can point us to the bug. Here's the updated program:

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

With the above error, “this invariant could not be proved on entry”, we know that the problem occurs before the loop and relates to the value of `left` and `right`, so it should be easy for us to see that we need to correct the initial assignment to right. If we would have changed the assignment to left to var `left = -1;`, then the error on the second invariant would be resolved, but the first invariant would show an error, so we must update the assignment to right.

### Contracts
Besides detecting exceptions, JVerify allows you to precisely state what a method does, and check that it actually does that. While the above binary search method would be correct after fixing the bug, it only works as intended if the array passed to it is sorted. We can guarantee that the binarySearch method works as intended by giving it a contract, using calls to the JVerify methods `precondition` and `postcondition`. Here’s the example from above but with a contract, and a fix for the bug we detected before:

```java
class BinarySearch {
  int binarySearch(int[] arr, int target) {

    // Without the following precondition, we won't be able to prove the
    // last two loop invariants
    precondition(sorted(arr));
    
    // This postcondition guarantees that the method behaves as desired
    postcondition((Integer r) -> sequence(arr).contains(target)
      ? 0 <= r && r < arr.length && arr[r] == target
      : r == -1
    );

    var left = 0;
    var right = arr.length - 1;
    
    while (left < right) 
    {
      invariant(0 <= left);
      invariant(left <= right);
      invariant(right <= arr.length);
      invariant(!drop(arr, left).contains(target));
      invariant(!take(arr, right).contains(target));

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

  @Pure // enables calling sorted in a contract
  @Erased // enables the call to forall in the body
  boolean sorted(int[] arr)
  { 
    reads(arr); // necessary to access arr in a @Pure method

    return forall((Integer i, Integer j) -> 
            implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j]));
  }
}
```

# This guide

This guide assumes that you're already familiar with Java. Specifications used by JVerify are written using regular Java expressions. However, verifying the correctness of Java code uses concepts that do not exist in regular Java, such as pre- and post-conditions. You can view JVerify as extending the Java language, even though it does not introduce any new syntax. This guide will walk you through the concepts that JVerify introduces.

JVerify uses a tool called a Satisfiability Modulo Theories (SMT) solver to help prove program correctness. Using this tool, JVerify will intelligently search through a space of proofs. For simple proofs, it can find them without help. For more complex proofs however, JVerify needs hints from you, the programmer. This guide will help you understand when JVerify does or does not need hints, and how to provide those.
