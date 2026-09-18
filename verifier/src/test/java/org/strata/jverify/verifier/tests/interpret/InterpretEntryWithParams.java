package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * A parameterized {@code @InterpretEntry} method is rejected at translation with
 * a clear diagnostic: the interpreter runs the marked method as a Core entry
 * procedure, and Strata rejects entry procedures that take inputs. Without this
 * gate, the stray {@code entry} clause aborts Laurel-to-Core translation for the
 * whole compilation unit, taking otherwise-verifiable siblings down with it.
 *
 * The marked method flips to Skipped, and its no-argument sibling still verifies.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 2,
        methodsSkipped = 1,
	methodsInvalid = 0,
        errorCount = 0
)
class InterpretEntryWithParams {
    @InterpretEntry
    static void runWithArg(int x) {
//              ^ error: @InterpretEntry method 'runWithArg' must take no arguments
        check(x >= 0);
    }

    static void supported(int y) {
        check(y == y);
    }
}
