package com.aws.jverify.verifier.tests.javasupport.statements;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

/**
 * Demonstrates that assert statements and check() calls are equivalent
 * for verification purposes.
 */
@JVerifyTest(exitCode = 0, methodsVerified = 4, failedAssertions = 0)
class AssertVsCheck {
    static void usingCheck(int x) {
        precondition(x > 0);
        check(x > 0);
        check(x >= 1);
        check(x != 0);
    }
    
    static void usingAssert(int x) {
        precondition(x > 0);
        assert x > 0;
        assert x >= 1;
        assert x != 0;
    }
    
    static void mixedUsage(int x, int y) {
        precondition(x > 0);
        precondition(y > x);
        
        check(x > 0);
        assert y > x;
        check(y > 0);
        assert y >= 2;
    }
}
