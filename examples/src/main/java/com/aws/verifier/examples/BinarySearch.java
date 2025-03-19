package com.aws.verifier.examples;

import com.aws.jverify.*;

import static com.aws.jverify.JVerify.*;

class BinarySearch {
  @Erased 
  @Pure
  boolean sorted(int[] arr)
  {
    reads(arr);

    return forall((Integer i) -> forall((Integer j) -> 
      implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j])));
  }

  int binarySearch(int[] arr, int target) {
      precondition(sorted(arr));
      postcondition((Integer r) -> sequence(arr).contains(target) 
        ? 0 <= r && r < arr.length && arr[r] == target 
        : r == -1
      );

      var left = 0;
      var right = arr.length - 1; // Bug: should be arr.Length
      
      while (left < right) 
      {
        invariant(0 <= left && left <= right && right <= arr.length);
        invariant(!sequence(arr).subsequence(left, right).contains(target));

        var mid = (left + right) / 2;
        if (arr[mid] == target) {
//          ^^^^^^^^ JSpec error: index out of range
            return mid;
        }
        if (arr[mid] < target) {
            left = mid + 1;
        } else {
            right = mid - 1; // Bug: should be just mid
        }
      }
      return -1;
  }

  @Pure
  boolean implies(boolean ante, boolean cons) {
    return !ante || cons;
  }

}