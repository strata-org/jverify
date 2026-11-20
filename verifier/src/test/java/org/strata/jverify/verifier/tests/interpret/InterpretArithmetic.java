package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Interpreter-only test: the @InterpretEntry method is concretely executed and
 * its {@code check} is evaluated as a runtime assertion. With {@code x = 5},
 * {@code y} computes to {@code 7}, so {@code check(y == 0)} fails and is mapped
 * back to the exact source range below. The entry point is found via the
 * producer's @InterpretEntry marker — no --entry flag.
 */
@JVerifyTest(
        interpret = true,
        exitCode = 4,
        methodsVerified = 1,
        methodsInvalid = 1,
        errorCount = 1)
class InterpretArithmetic {
    @InterpretEntry
    static void run() {
        int x = 5;
        int y = x * 2 - 3;
        check(y == 0);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
