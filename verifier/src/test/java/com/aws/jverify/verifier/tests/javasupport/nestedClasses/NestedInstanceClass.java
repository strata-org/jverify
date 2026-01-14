package com.aws.jverify.verifier.tests.javasupport.nestedClasses;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, methodsVerified = 4, errorCount = 2)
public class NestedInstanceClass {
    int x;
    
    @Nullable InstanceNestee nestee;
    public class InstanceNestee {
        void checkFalse() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion does not hold
        }

        void checkX() {
            check(x == 3);
//          ^^^^^^^^^^^^^ Error: assertion does not hold
        }

        @SuppressWarnings("ConstantValue")
        void usesThisses() {
            check(NestedInstanceClass.this instanceof NestedInstanceClass);
            check(this instanceof InstanceNestee);
        }
    }
}

class InstanceNestee {
    @Nullable
    InstanceNestee fakeNestee;
    @Nullable NestedInstanceClass.InstanceNestee nestee;
}
