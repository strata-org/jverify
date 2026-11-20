package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * The two `throw` refusals: a throw needs
 * the enclosing method to declare the thrown type, and the thrown type must
 * fit under what is declared. Both are legal Java for unchecked exceptions --
 * javac only polices checked ones -- so the frontend enforces the
 * declared-throws discipline itself, per method, leaving siblings verifiable
 * (same skip machinery TranslatorSkip pins).
 */
// 'undeclared' is translated and fails verification at its reachable throw
// instead of being refused: an undeclared unchecked throw is an error only if
// it can happen.
@JVerifyTest(
        expectedToFail = true,
        blockedBy = Blocker.FRONTEND_EXCEPTIONS,
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 4, // fine() + 3 implicit constructors (2 nested + the test class)
        methodsSkipped = 3,
        methodsInvalid = 0, // also arms the errorCount expectation below
        errorCount = 0
)
class ThrowDiagnostics {
    static class Boom extends RuntimeException {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.RuntimeException'
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'

    /** Unchecked throw with no declaration: legal Java, refused here. */
    static int undeclared(int x) {
//             ^ error: throw requires the enclosing method to declare the thrown type in its throws clause
        if (x < 0) {
            throw new Boom();
        }
        return x;
    }

    /** Unchecked throw outside the declared hierarchy: also legal Java, but
        it would escape the declared exceptional contract. */
    static int notCovered(int x) throws AppException {
//             ^ error: thrown type 'org.strata.jverify.verifier.tests.javasupport.exceptions.ThrowDiagnostics.Boom' is not covered by the declared throws clause ('org.strata.jverify.verifier.tests.javasupport.exceptions.ThrowDiagnostics.AppException')
        if (x < 0) {
            throw new Boom();
        }
        return x;
    }

    /** A thrown STRICT SUPERTYPE of the declared type: values of the wider
        type do not fit through the narrower declared exit. Only reachable
        with unchecked exceptions -- javac itself polices checked ones. */
    static int throwsSupertypeOfDeclared(int x) throws Boom {
//             ^ error: thrown type 'java.lang.RuntimeException' is not covered by the declared throws clause ('org.strata.jverify.verifier.tests.javasupport.exceptions.ThrowDiagnostics.Boom')
        if (x < 0) {
            throw new RuntimeException();
        }
        return x;
    }

    /** Siblings stay verifiable. */
    static int fine(int x) {
        return x;
    }
}
