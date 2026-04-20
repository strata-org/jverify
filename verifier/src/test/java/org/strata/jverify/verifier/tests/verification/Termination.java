package org.strata.jverify.verifier.tests.verification;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 2, errorCount = 1)
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
//              ^^^^^^^^^^^^^^^^^^^^ Error: decreases clause could not be proved to decrease
            }
        }
        else {
            WrongOrder(x - 1, y);
        }
    }
}
