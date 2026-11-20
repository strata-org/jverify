package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code --entry pkg.Class#method} picks the entry point in place of every
 * {@code @InterpretEntry} marker: only {@code run}'s failing {@code check} is
 * reported, not {@code marked}'s.
 */
@JVerifyTest(
        interpret = true,
        entry = "org.strata.jverify.verifier.tests.interpret.InterpretEntryFlag#run",
        exitCode = 4,
        methodsVerified = 2,
        methodsInvalid = 1,
        errorCount = 1)
class InterpretEntryFlag {
    @InterpretEntry
    static void marked() {
        check(false);
    }

    static void run() {
        int x = 5;
        check(x == 0);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
