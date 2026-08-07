package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

/**
 * Phase 3 of exception support (docs/exceptions-design.md, Phasing §3):
 * {@code try}/{@code catch}/{@code finally} lower to Laurel's {@code Try}
 * node. A catch arm's type dispatch becomes the guard {@code e is E}
 * (multi-catch: a disjunction), arms keep source order (both sides are
 * first-match-wins), and the unwinding semantics -- a throw, return, or
 * break crossing a {@code finally} runs the arm -- is Laurel's job, not
 * encoded here.
 *
 * <p>A throw caught by an enclosing arm needs no declared {@code throws},
 * mirroring javac's own checked-exception rule; that is what lets a
 * non-throwing method use try/catch internally.
 */
@JVerifyTest(methodsVerified = 12) // 8 static methods + 4 implicit constructors
class TryCatchFinally {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}
    static class IoError extends AppException {}

    /** Catch an unconditional local throw: the handler is the only exit. */
    static int catchLocal() {
        postcondition((int r) -> r == 1);
        try {
            throw new AppException();
        } catch (AppException e) {
            return 1;
        }
    }

    /** The subtype arm wins over the ancestor arm, in source order. The
        thrown value is statically typed at the ancestor (also keeping javac's
        unreachable-catch analysis quiet); dispatch is on the dynamic type. */
    static int firstMatchWins() {
        postcondition((int r) -> r == 1);
        try {
            AppException e = new ParseError();
            throw e;
        } catch (ParseError e) {
            return 1;
        } catch (AppException e) {
            return 2;
        }
    }

    /** The ancestor arm catches a thrown subtype. */
    static int ancestorCatches() {
        postcondition((int r) -> r == 2);
        try {
            throw new ParseError();
        } catch (AppException e) {
            return 2;
        }
    }

    /** Multi-catch covers both (unrelated) alternatives through one arm. */
    static int multiCatch(boolean b) {
        postcondition((int r) -> r == 3);
        try {
            if (b) {
                throw new ParseError();
            } else {
                throw new IoError();
            }
        } catch (ParseError | IoError e) {
            return 3;
        }
    }

    /** Catching a callee's declared exception at the call site. */
    static int mayFail(int x) throws AppException {
        if (x < 0) {
            throw new AppException();
        }
        return x;
    }

    static int catchFromCall(int x) {
        try {
            return mayFail(x);
        } catch (AppException e) {
            return 0;
        }
    }

    /** finally runs on the normal path... */
    static int finallyOnNormal(int x) {
        postcondition((int r) -> r >= 1);
        int count = 0;
        try {
            count = count + 1;
        } finally {
            count = count + 1;
        }
        check(count == 2);
        return count;
    }

    /** ...and on the throwing path, before the arm one try out handles it. */
    static int finallyOnThrow() {
        postcondition((int r) -> r == 11);
        int count = 0;
        try {
            try {
                throw new AppException();
            } finally {
                count = count + 10;
            }
        } catch (AppException e) {
            return count + 1;
        }
    }
}
