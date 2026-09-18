package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * The two `throw` refusals (docs/exceptions-design.md §1, §5): a throw needs
 * the enclosing method to declare the thrown type, and the thrown type must
 * fit under what is declared. Both are legal Java for unchecked exceptions --
 * javac only polices checked ones -- so the frontend enforces the
 * declared-throws discipline itself, per method, leaving siblings verifiable
 * (same skip machinery TranslatorSkip pins).
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 4, // fine() + 3 implicit constructors (2 nested + the test class)
        methodsSkipped = 2,
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

    /** Siblings stay verifiable. */
    static int fine(int x) {
        return x;
    }
}
