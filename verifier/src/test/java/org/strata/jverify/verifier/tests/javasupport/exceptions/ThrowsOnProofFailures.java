package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.throwsOn;

/**
 * The guard restriction, pinned red (docs/exceptions-design.md §3): a
 * {@code throwsOn} guard is a pre-state predicate over parameters, but the
 * lowering places it in postconditions, so a heap-reading guard would
 * silently mean "held on exit" -- Strata's resolution rejects the shape with
 * its own diagnostic, surfaced here through the whole pipeline.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 4, // verification errors present
        methodsVerified = 5, // fine(), readCell(), + 3 implicit constructors
        methodsInvalid = 1, // heapGuard(); also arms the errorCount expectation
        errorCount = 1
)
class ThrowsOnProofFailures {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class Cell {
        int val;
    }

    /** A read of Cell.val: field registration is read-driven today (a
        write-only field fails resolution -- pre-existing, tracked
        separately). */
    static boolean readCell(Cell c) {
        return c.val > 0;
    }

    /** A heap-reading guard, rejected by Strata's resolution. */
    static int heapGuard(Cell c, int x) throws AppException {
        throwsOn(c.val > 0);
//               ^^^^^^^^^ Error: a `throwsOn` guard that reads the heap is not yet supported: a guard is evaluated in the pre-state by definition, but the lowering reads it in the post-state, so a guard naming a field (`x#f`) or calling a heap-reading procedure would silently mean "held on exit". Restrict the guard to parameters.
        if (c.val > 0) {
            throw new AppException();
        }
        return x;
    }

    /** Siblings stay verifiable. */
    static int fine(int x) {
        return x;
    }
}
