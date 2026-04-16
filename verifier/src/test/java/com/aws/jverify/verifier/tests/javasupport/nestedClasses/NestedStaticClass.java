package com.aws.jverify.verifier.tests.javasupport.nestedClasses;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

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
