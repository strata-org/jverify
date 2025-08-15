package com.aws.jverify.verifier.tests.javasupport;

import com.aws.jverify.Nat;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 2, dafnyErrors = 3)
class PrimitiveTypes {
    public void foo(int s) {
        short x = 10;
        int y = 20;
        long z = 30;

        if (s == 0) {
            @Nat short a = -10;
//                         ^^^ Error: value does not satisfy the subset constraints of 'nat15'
        } else if (s == 1) {
            @Nat int b = -20;
//                       ^^^ Error: value does not satisfy the subset constraints of 'nat31'
        } else if (s == 2) {
            @Nat long c = -30;
//                        ^^^ Error: value does not satisfy the subset constraints of 'nat63'
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
