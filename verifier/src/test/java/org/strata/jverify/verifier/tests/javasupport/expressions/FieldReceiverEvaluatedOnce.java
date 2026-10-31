package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(methodsVerified = 4, methodsInvalid = 1, errorCount = 1, exitCode = 4)
class FieldReceiverEvaluatedOnce {
    static final class Box {
        int f;
    }

    static Box pick(Box a, Box b, int i) {
        postcondition((Box r) -> r == (i > 0 ? a : b));
        return i > 0 ? a : b;
    }

    // A field read evaluates its receiver once: `++x` runs once.
    static void receiverRunsOnce() {
        int x = 1;
        Box o = new Box();
        int r = pick(o, o, ++x).f;
        check(x == 2);
    }

    static void receiverRunsOnceKO() {
        int x = 1;
        Box o = new Box();
        int r = pick(o, o, ++x).f;
        check(x != 2);
//      ^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
