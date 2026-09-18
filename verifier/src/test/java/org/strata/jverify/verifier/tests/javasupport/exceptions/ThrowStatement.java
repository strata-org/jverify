package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * Phase 2 of exception support (docs/exceptions-design.md, Phasing §2): the
 * {@code throw} statement lowers to Laurel's exceptional channel. The
 * enclosing method's {@code throws} clause (phase 1) is the anchor: the
 * thrown expression's static type must fit under the declared join, and the
 * chain from the thrown class up to it is declared so the Laurel
 * {@code throw} type-checks.
 *
 * <p>Each method here verifies against the derived exceptional contract
 * ({@code isBad ==> err is <declared>}); the forcing direction ("this input
 * definitely throws") arrives with the contract DSL phase.
 */
@JVerifyTest(methodsVerified = 8) // 5 static methods + 3 implicit constructors (2 nested + the test class)
class ThrowStatement {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}

    /** Unconditional throw. */
    static int alwaysThrows() throws AppException {
        throw new AppException();
    }

    /** Conditional throw beside a normal return. */
    static int guarded(int x) throws AppException {
        if (x < 0) {
            throw new AppException();
        }
        return x;
    }

    /** The thrown type may sit below the declared one; the chain
        {@code ParseError -> AppException} is declared for the type-check. */
    static int throwsSubtype(int x) throws AppException {
        if (x < 0) {
            throw new ParseError();
        }
        return x;
    }

    /** Propagation: the callee's Bad path re-throws in the caller. */
    static int propagates(int x) throws AppException {
        return guarded(x);
    }

    /** A throw as a switch rule body: SwitchDesugarer turns the statement
        form into an if-cascade whose branch is now a supported statement. */
    static int switchThrow(int i) throws AppException {
        switch (i) {
            case 0 -> throw new AppException();
            default -> { }
        }
        return i;
    }
}
