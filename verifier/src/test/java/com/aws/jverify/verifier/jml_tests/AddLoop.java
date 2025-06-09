package com.aws.jverify.verifier.jml_tests;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class AddLoop {
    //@ requires true;
    //@ ensures \result == x + y;
    //@ code_bigint_math
    public static int AddLoop(int x, int y) {
        // Translation of the requires spec
        precondition(x+y < Integer.MAX_VALUE && x+y>Integer.MIN_VALUE);
        precondition(y > Integer.MIN_VALUE);
        // Translation of the ensures spec
        postcondition((Integer result) -> result == x + y);
        int sum = x;
        if (y > 0) {
            int n = y;

            //@ maintaining sum == x + y - n && 0 <= n;
            //@ decreases n;
            while (n > 0) {
                // Translation of the maintaining spec
                invariant(sum == x + y - n && 0 <= n);
                // Translation of the decreases spec
                decreases(n);
                sum = sum + 1;
                n = n - 1;
            }
        } else {
            int n = -y;

            //@ maintaining sum == x + y + n && 0 <= n;
            //@ decreases n;
            while (n > 0) {
                // Translation of the maintaining spec
                invariant(sum == x + y + n && 0 <= n);
                // Translation of the decreases spec
                decreases(n);
                sum = sum - 1;

                n = n - 1;
            }
        }
        return sum;

    }
}
