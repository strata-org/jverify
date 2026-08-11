package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * try-with-resources today (docs/exceptions-design.md, Decision 4): javac's
 * LOWER phase (already in the pipeline) desugars the form into try/finally
 * before translation -- verified by this fixture, whose resources never reach
 * the translator's defensive refusal. What survives is the desugared
 * {@code catch (Throwable t) ... throw t}, whose re-throw answers to the
 * declared-throws rule like any other; a method that cannot cover
 * {@code Throwable} is therefore refused with the declared-throws diagnostic
 * below. A friendlier per-construct message is future work.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 4, // fine(), Res.close(), and 2 implicit constructors
        methodsSkipped = 1,
        methodsInvalid = 0, // also arms the errorCount expectation below
        errorCount = 0
)
class TryWithResources {
    static class Res implements AutoCloseable {
        public void close() {}
    }

    /** The desugared re-throw needs a declared throws clause this method
        does not have. */
    static int useResource(int x) {
//             ^ error: throw requires the enclosing method to declare the thrown type in its throws clause
        try (Res r = new Res()) {
            return x;
        }
    }

    /** Siblings stay verifiable. */
    static int fine(int x) {
        return x;
    }
}
