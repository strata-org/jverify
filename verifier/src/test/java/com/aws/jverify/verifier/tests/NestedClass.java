package com.aws.jverify.verifier.tests;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 3, dafnyErrors = 2)
public class NestedClass {

    private final int x;

    @Nullable Nestee nestee;

    public NestedClass(int x) {
        this.x = x;
    }

    public Nestee makeNestee() {
        return new Nestee();
    }

    public class Nestee {
        void checkFalse() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion might not hold
        }

        void checkX() {
            check(x == 3);
//          ^^^^^^^^^^^^^ Error: assertion might not hold
        }
    }
}

class Nestee {
    @Nullable Nestee fakeNestee;
    @Nullable NestedClass.Nestee nestee;
}
