package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 2, errorCount = 0)
public class InheritingOverriddenUnverifiedPureMethod {
    interface Common {
        @Pure
        @Verify(false)
        default int foo() { return 3; }
    }
    interface Left extends Common {
        @Pure
        @Verify(false)
        default int foo() { return 4; }
    }
    
    static class Leaf implements Left {}
}
