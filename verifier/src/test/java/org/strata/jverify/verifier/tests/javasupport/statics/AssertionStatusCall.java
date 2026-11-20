package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code Class.desiredAssertionStatus()} is replaced by {@code true} only
 * where javac emits it, in {@code <clinit>} computing {@code $assertionsDisabled}.
 * A call the user writes is translated like any other call on a class
 * literal, which is not supported, instead of being proved true.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 2, methodsSkipped = 1, errorCount = 0)
class AssertionStatusCall {

    static void probe() {
//              ^ error: call to 'desiredAssertionStatus' on a receiver whose static type is not a class is not yet supported
        check(AssertionStatusCall.class.desiredAssertionStatus());
    }

    static int withAssert(int x) {
        assert x == x;
        return x;
    }
}
