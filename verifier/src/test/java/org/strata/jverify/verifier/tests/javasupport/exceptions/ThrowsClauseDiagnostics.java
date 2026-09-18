package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * The {@code throws}-clause refusal (docs/exceptions-design.md §1): a thrown
 * type must resolve to a class. A generic exception parameter
 * ({@code <X extends Exception> ... throws X}) has no class to declare as the
 * exceptional exit, so the method is refused rather than mis-modelled at the
 * bound. Per method, leaving siblings verifiable.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 2, // fine() + the test class's implicit constructor
        methodsSkipped = 1,
        methodsInvalid = 0, // also arms the errorCount expectation below
        errorCount = 0
)
class ThrowsClauseDiagnostics {

    /** A type-variable throws clause: no class to hang the exceptional exit on. */
    static <X extends Exception> int genericThrows(int x) throws X {
//                                   ^ error: Unsupported thrown type in throws clause: X
        return x;
    }

    /** Siblings stay verifiable. */
    static int fine(int x) {
        return x;
    }
}
