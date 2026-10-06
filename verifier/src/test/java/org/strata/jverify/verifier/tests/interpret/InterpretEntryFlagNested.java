package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/** {@code --entry} names a nested class by its canonical name, as Java source does. */
@JVerifyTest(
        interpret = true,
        entry = "org.strata.jverify.verifier.tests.interpret.InterpretEntryFlagNested.Inner#run",
        exitCode = 4,
        methodsVerified = 2,
        methodsInvalid = 1,
        errorCount = 1)
class InterpretEntryFlagNested {
    static class Inner {
        static void run() {
            int x = 5;
            check(x == 0);
//          ^^^^^^^^^^^^^ Error: assertion does not hold
        }
    }
}
