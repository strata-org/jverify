package com.aws.jverify.verifier.tests.javasupport.nestedClasses;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 14, dafnyErrors = 1)
public class NestedStaticClass {
    @Nullable
    StaticNestee nestee;
    
    public static class StaticNestee {
        void checkFalse() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion could not be proved
        }
    }
}

class StaticNestee {
    @Nullable
    StaticNestee fakeNestee;
    NestedStaticClass.@Nullable StaticNestee nestee;
}
