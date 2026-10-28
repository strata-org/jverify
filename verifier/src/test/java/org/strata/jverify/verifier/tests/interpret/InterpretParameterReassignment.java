package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Interpret-mode regression test for parameter reassignment on the (default)
 * Core route. Core forbids a procedure from writing its inputs; before
 * Strata's {@code CopyMutatedInputs} pass, {@code a = a + 1} made Core type
 * checking fail ("This procedure modifies variables it is not allowed to")
 * and the whole program aborted with zero checks executed, while the Laurel
 * interpreter ran the identical program fine. Minimized from a fuzz-generated
 * program of nested while loops whose body reassigned a parameter.
 */
@JVerifyTest(interpret = true, methodsVerified = 3, errorCount = 0)
class InterpretParameterReassignment {
    static int f(int a) {
        a = a + 1;
        return a;
    }

    @InterpretEntry
    static void run() {
        check(f(4) == 5);
    }
}
