package com.aws.jverify.verifier.tests;

import com.aws.jverify.Nat;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.decreases;

@JVerifyTest(exitCode = 4, dafnyVerified = 2, dafnyErrors = 1)
class Termination {
    void Recursive(@Nat int x, @Nat int y) {
        decreases(y, x);
        
        if (x == 0) {
            if (y == 0) {
            } else {
                Recursive(y, y - 1);
            }
        }
        else {
            Recursive(x - 1, y);
        }
    }

    void WrongOrder(@Nat int x, @Nat int y) {
        decreases(x, y);

        if (x == 0) {
            if (y == 0) {
            } else {
                WrongOrder(y, y - 1);
//              ^^^^^^^^^^^^^^^^^^^^ Error: decreases clause might not decrease
            }
        }
        else {
            WrongOrder(x - 1, y);
        }
    }
}
