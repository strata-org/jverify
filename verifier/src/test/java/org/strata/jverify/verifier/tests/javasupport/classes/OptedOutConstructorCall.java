package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * A call to an opted-out constructor is refused at the call site, exactly as a
 * call to an opted-out method is: the constructor's body was never translated,
 * so dropping the call would let the caller assume it changes nothing.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 3, methodsSkipped = 4, errorCount = 0)
class OptedOutConstructorCall {
    static class Box {
        int n;
    }

    static class Sink {
        @Verify(false)
        Sink(Box b) {
            b.n = 99;
        }
    }

    static class Writer {
        @Verify(false)
        static void write(Box b) {
            b.n = 99;
        }
    }

    static void callsConstructor(Box b) {
//              ^ error: call to '<init>', which could not be translated
        b.n = 0;
        new Sink(b);
        check(b.n == 0);
    }

    static void callsMethod(Box b) {
//              ^ error: call to 'write', which could not be translated
        b.n = 0;
        Writer.write(b);
        check(b.n == 0);
    }
}
