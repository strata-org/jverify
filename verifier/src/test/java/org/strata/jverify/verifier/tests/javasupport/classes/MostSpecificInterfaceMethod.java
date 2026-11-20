package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

/**
 * javac binds {@code c.get(b)} to {@code J.get(Box)}, which overrides {@code I.get(Box)} and is opted out, so the
 * call is refused, as one of any opted-out method is: {@code D} runs it, and it returns 5.
 */
@JVerifyTest(exitCode = 2)
class MostSpecificInterfaceMethod {
    static class Box {
    }

    interface I {
        int get(Box b);
    }

    interface J extends I {
        @Verify(false)
        default int get(Box b) {
            postcondition((int r) -> r == 0);
            return 5;
        }
    }

    abstract static class C implements I, J {
    }

    static class D extends C {
    }

    static void use(C c, Box b) {
//                              ^ error: call to 'get', which could not be translated
        check(c.get(b) == 0);
    }
}
