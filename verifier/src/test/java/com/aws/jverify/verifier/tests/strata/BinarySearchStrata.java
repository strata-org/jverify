package com.aws.jverify.verifier.tests.strata;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;
import static com.aws.jverify.verifier.Backend.Dafny;
import com.aws.jverify.Erased;
import com.aws.jverify.Pure;

@JVerifyTest(exitCode = 0, BACKENDS = {Strata})
class BinarySearchStrata {

    static int findIndex(int[] arr, int key) {
        postcondition((int res) ->
                (res == -1 && !sequence(arr).contains(key))
                        || (0 <= res && res < arr.length && arr[res] == key)
        );
        precondition(sorted(arr));
        precondition(arr.length <= 1000000000);

        int lo = 0;
        int hi = arr.length;
        while (lo < hi) {
            invariant(0 <= lo);
            invariant(lo <= hi);
            invariant(hi <= arr.length);
            invariant(!sequence(arr).take(lo).contains(key));
            invariant(!sequence(arr).drop(hi).contains(key));

            int mid = lo + (hi - lo) / 2;
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
