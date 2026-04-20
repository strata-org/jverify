package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Nullable;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 3, errorCount = 1)
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
