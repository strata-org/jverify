package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.throwsOn;

/**
 * Translator-level refusals of the exceptional-contract DSL
 * (docs/exceptions-design.md §3): a case needs a declared {@code throws}
 * clause to hang on, and a case postcondition's parameter must be typed at
 * the declared throws type. Both reject per method, leaving siblings
 * verifiable (the same skip machinery TranslatorSkip pins).
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 4, // fine() + 3 implicit constructors
        methodsSkipped = 2,
        errorCount = 0
)
class ThrowsOnSkips {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}

    /** A case without a declared throws clause. */
    static int caseWithoutThrows(int x) {
//             ^ error: throwsOn requires the method to declare the thrown type in its throws clause
        throwsOn(x < 0);
        return x;
    }

    /** A case postcondition's parameter typed below the declared throws type. */
    static int narrowPostconditionParam(int x) throws AppException {
//             ^ error: a throwsOn case's postcondition parameter must be typed at the method's declared throws type ('org.strata.jverify.verifier.tests.javasupport.exceptions.ThrowsOnSkips.AppException'); narrow with instanceof and a cast inside the property
        throwsOn(x < 0).postcondition((ParseError e) -> true);
        if (x < 0) {
            throw new ParseError();
        }
        return x;
    }

    /** Siblings stay verifiable. */
    static int fine(int x) {
        return x;
    }
}
