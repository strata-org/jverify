package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A {@code for} loop with no invariant: the check after it cannot be proved, and
 * the loop's implicit "invariant holds on entry" obligation is reported against
 * the {@code for} keyword.
 *
 * <p>Keeps {@code -Djverify.strictSourceRanges=true} load-bearing. That flag hard-fails
 * on a diagnostic against a source-less synthesized node, but only for an obligation
 * that actually FAILS, and once every other loop fixture got a sufficient invariant
 * nothing in the suite triggered it. Verified by reverting the range fix: without this
 * fixture the suite catches the regression only in {@code LoopNodeRangeEmitTest}; with
 * it, strict mode fires here too.
 *
 * <p>The first range is the {@code for} keyword. javac records no end position for a
 * {@code JCForLoop}, so the range would otherwise be zero-width -- underlining
 * nothing, and inexpressible with the {@code ^^^} convention.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 1, methodsInvalid = 1, errorCount = 2)
class BareForLoop {
    static void countWithoutInvariant() {
        int i = 0;
        for (i = 0; i < 5; i = i + 1) {
//      ^^^ Error: assertion could not be proved
        }
        check(i == 5);
//      ^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
