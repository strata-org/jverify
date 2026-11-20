package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * Phase 1 of exception support: a
 * method's Java {@code throws} clause becomes the procedure's declared Laurel
 * exceptional exit ({@code throws (e: T)}), exception classes become
 * composites with their {@code extends} chains, and multi-{@code throws}
 * joins to the least common ancestor.
 *
 * <p>No {@code throw}, {@code try}, or exceptional contract DSL yet (later
 * phases). The bodies here never throw, so each method verifies against its
 * vacuous exceptional contract; what the phase buys is the signature itself:
 * callers see the exceptional exit, and a call from one throwing method to
 * another needs no handler because Strata synthesizes the propagation at the
 * call site.
 */
@JVerifyTest(methodsVerified = 8) // 4 static methods + 4 implicit constructors (3 nested + the test class)
class ThrowsSignatures {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}
    static class IoError extends AppException {}

    /** Declared exceptional exit, body never throws: verifies vacuously. */
    static int declared(int x) throws AppException {
        return x;
    }

    /** Multi-throws joins to the least common ancestor ({@code AppException});
        the chains {@code ParseError -> AppException} and
        {@code IoError -> AppException} are declared as composite extends. */
    static int multi(int x) throws ParseError, IoError {
        return x;
    }

    /** A thrown type alongside its own ancestor joins at the ancestor. */
    static int mixedDepths(int x) throws ParseError, AppException {
        return x;
    }

    /** Propagation: a throwing method calls a throwing method with no handler.
        Strata rewrites the call site to unwrap the callee's Result and
        re-throw on the Bad path, so this verifies without any frontend
        statement support. */
    static int propagates(int x) throws AppException {
        return declared(x);
    }
}
