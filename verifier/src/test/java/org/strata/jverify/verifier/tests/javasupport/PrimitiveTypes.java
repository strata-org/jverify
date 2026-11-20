package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, methodsVerified = 1, errorCount = 3)
class PrimitiveTypes {
    public void foo(int s) {
        short x = 10;
        int y = 20;
        long z = 30;

        if (s == 0) {
            @Nat short a = -10;
//                         ^^^ Error: assertion does not hold
            x = a;
        } else if (s == 1) {
            @Nat int b = -20;
//                       ^^^ Error: assertion does not hold
            y = b;
        } else if (s == 2) {
            @Nat long c = -30;
//                        ^^^ Error: assertion does not hold
            z = c;
        }

//        x = (short)y;
//        y = (int)z;
//        z = (long)x;
        
        char c = 'q';
        byte b = 10;
        float f;
        double d;
    }
}
