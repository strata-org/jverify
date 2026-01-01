package com.aws.jverify.verifier.tests.javasupport.classes;

import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(javaVerified = 6, javaErrors = 0)
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
