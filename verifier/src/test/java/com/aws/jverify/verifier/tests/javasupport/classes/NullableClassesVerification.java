package com.aws.jverify.verifier.tests.javasupport.classes;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, javaVerified = 3, javaErrors = 1)
public class NullableClassesVerification {
    
    static class C {
        void foo() {}
    }

    @SuppressWarnings("DataFlowIssue")
    void useClass(C c,
                  @Nullable C nullableC) {
        c.foo();
        if (nullableC == null) {
            nullableC.foo();
//          ^^^^^^^^^ Error: target object could not be proved to be non-null
        } else {
            nullableC.foo();
        }
    }
}
