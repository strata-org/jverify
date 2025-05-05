// TEST: exitCode=0 dafnyVerified=4 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

class Aux {
    int a;
    int b;
    Aux(int a, int b) {
        // Need a postcondition on the ctor to be able to prove better assertions in
        // newAuxArray
        this.a = a;
        this.b = b;
    }

    public int getB() {
        postcondition((Integer x) -> x == this.b);
        return b;
    }
}


@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
@JVerifyTest
class VerifyNewArray {

    void newIntArray() {
        int[] arr = new int[5];
        check(arr.length == 5);
        arr[0] = 0;
        for (var i = 1; i < 5; i++) {
            modifies(arr);
            invariant(arr[0] == 0);
            int finalI = i;
            // Crash with the invariant below
            // invariant(forall((Integer j) -> implies(0 <= j && j < finalI, arr[j] == 0)));
            arr[i] = i;
        }
        check(arr[0] == 0);
    }

    void newIntArray2() {
        int[][] arr2 = new int[5][3];
        // no support of length for multi-dimensional arrays yet
        // check(arr2.length == 5);
        // check(arr2[0].length == 3);
        arr2[0][0] = 0;
        for (var i = 1; i < 5; i++) {
            modifies(arr2);
            invariant(arr2[0][0] == 0);
            for (var j = 0; j < 3; j++) {
                modifies(arr2);
                invariant(arr2[0][0] == 0);

                arr2[i][j] = i + j;
            }
        }
        check(arr2[0][0] == 0);
    }


    void newAuxArray() {
        var arr = new Aux[15];
        check(arr.length == 15);
        arr[0] = new Aux(0, 0);
        int i;
        for (i = 1; i < 15; i++) {
            invariant(arr[0] != null);
            modifies(arr);
            int finalI = i;
            // Crash with the invariant below
            // invariant(forall((Integer j) -> implies(0<=j && j<finalI,arr[j] != null)));
            arr[i] = new Aux(i, i + 1);
        }
        check(i == 15);
    }

}
