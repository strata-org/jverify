package org.strata.jverify.verifier.tests.javasupport;

import static org.strata.jverify.JVerify.*;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, methodsVerified = 8, errorCount = 1)
class SameNamedMethods {

    static class A {
        int get() {
            postcondition((int r) -> r == 1);
            return 1;
        }
    }

    static class B {
        boolean get() {
            postcondition((boolean r) -> r);
            return true;
        }
    }

    static boolean k() {
        postcondition((boolean r) -> !r);
        return false;
    }

    static boolean pick(boolean c, B b) {
        postcondition((boolean r) -> r == c);
        return c ? b.get() : k();
    }

    static int pickInt(boolean c, A a) {
        postcondition((int r) -> r == 0 || c);
        return c ? a.get() : 0;
    }

    static void wrong(B b) {
        check(!pick(true, b));
//      ^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
