package com.aws.jverify.verifier.tests;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 3, dafnyErrors = 1)
public class NestedClass {
    @Nullable Nestee nestee;
    public class Nestee {
        void checkFalse() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion might not hold
        }
    }
}

class Nestee {
    @Nullable Nestee fakeNestee;
    @Nullable NestedClass.Nestee nestee;
}
