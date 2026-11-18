package org.strata.jverify.verifier.tests.javasupport.boxing;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A check on a box that holds another value fails at the check, after the
 * check before it has passed.
 */
@JVerifyTest(interpret = true, entry = "org.strata.jverify.verifier.tests.javasupport.boxing.BoxingExecutionCheckFails#run",
        exitCode = 4, methodsVerified = 1, methodsInvalid = 1, methodsSkipped = 0, errorCount = 1)
class BoxingExecutionCheckFails {
    static void run() {
        Integer i = 41;
        Integer j = i + 1;
        check(j.equals(42));
        check(i.equals(42));
//      ^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
