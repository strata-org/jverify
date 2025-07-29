package com.aws.jverify.verifier.tests.nestedClasses;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 0, dafnyErrors = 1)
public class NestedInstanceClass {
    @Nullable InstanceNestee nestee;
    public class InstanceNestee {
        void checkFalse() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion might not hold
        }
    }
}

class InstanceNestee {
    @Nullable
    InstanceNestee fakeNestee;
    @Nullable NestedInstanceClass.InstanceNestee nestee;
}
