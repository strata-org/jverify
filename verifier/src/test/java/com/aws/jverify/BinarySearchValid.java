package com.aws.jverify;

import static com.aws.jverify.JVerify.*;

class BinarySearchValid {
    @Pure
    @Erased
    static boolean sorted(int[] arr) {
        reads(arr);
        return forall((Integer i, Integer j) ->
                !(0 <= i && i < j && j < arr.length) || arr[i] < arr[j]);
    }

    public static int binarySearchImpl(int[] arr, int key) {
        precondition(arr.length <= 0x7fff_ffff /* Integer.MAX_VALUE */);
        precondition(sorted(arr));
        postcondition((Integer res) ->
                (res == -1 && !sequence(arr).contains(key))
                || (0 <= res && res < arr.length && arr[res] == key)
        );

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
}
