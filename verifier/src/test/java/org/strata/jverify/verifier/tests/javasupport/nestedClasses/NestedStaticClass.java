package org.strata.jverify.verifier.tests.javasupport.nestedClasses;

import org.strata.jverify.Nullable;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 3, errorCount = 1)
public class NestedStaticClass {
    @Nullable
    StaticNestee nestee;
    
    public static class StaticNestee {
        void checkFalse() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion does not hold
        }
    }
}

class StaticNestee {
    @Nullable
    StaticNestee fakeNestee;
    NestedStaticClass.@Nullable StaticNestee nestee;
}
