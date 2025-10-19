package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;

import com.aws.jverify.Erased;
import com.aws.jverify.Pure;

@SuppressWarnings("ConstantValue")
class BinarySearch {

    public static int findIndex(int[] arr, int key) {
        postcondition((int res) ->
                (res == -1 && !sequence(arr).contains(key))
                        || (0 <= res && res < arr.length && arr[res] == key)
        );
        precondition(arr.length <= Integer.MAX_VALUE);
        precondition(sorted(arr));

        var lo = 0;
        var hi = arr.length;
        while (lo < hi) {
            invariant(0 <= lo);
            invariant(lo <= hi);
            invariant(hi <= arr.length);
            invariant(!sequence(arr).take(lo).contains(key));
            invariant(!sequence(arr).drop(hi).contains(key));

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
    
    @Pure
    @Erased
    static boolean sorted(int[] arr) {
        reads(arr);
        return forall((int i, int j) ->
                !(0 <= i && i < j && j < arr.length) || arr[i] < arr[j]);
    }
}
