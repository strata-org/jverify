package org.strata.jverify.verifier.tests.javasupport.nestedClasses;

import org.strata.jverify.Nullable;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_INSTANCE_METHODS}, exitCode = 4, methodsVerified = 4, errorCount = 2)
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
