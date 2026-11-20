package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

/**
 * Phase 3 of exception support:
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
@JVerifyTest(methodsVerified = 21) // 16 static methods + 5 implicit constructors
class TryCatchFinally {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}
    static class IoError extends AppException {}
    static class Boom extends RuntimeException {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.RuntimeException'

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

    /** Catch a SUPERTYPE of an exception that only arrives by propagation
        (no local throw): the guard `e is Exception` must see the chain
        {@code AppException -> Exception}, which the callee's throws clause
        declares up to Throwable. Scoping that registration to the declared
        join would leave the sorts unrelated -- an unrelated-type resolution
        error plus a may-escape error, which this file's exact diagnostics
        would surface. (Which values flow where is a phase-4 concern; the
        pin here is that the shape translates and verifies at all.) */
    static int supertypeCatchOfPropagated(int x) {
        try {
            return mayFail(x);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Propagation through a multi-throws join is catchable at the join:
        the carrier is typed at the LCA ({@code AppException}), so a catch of
        exactly that type covers it. A join wrongly computed wider (say
        {@code Throwable}) would surface here as a may-escape error. */
    static int joinThrower(int x) throws ParseError, IoError {
        if (x < 0) {
            throw new ParseError();
        }
        if (x > 100) {
            throw new IoError();
        }
        return x;
    }

    static int joinDispatch(int x) {
        try {
            return joinThrower(x);
        } catch (AppException e) {
            return 1;
        }
    }

    /** A join forced to a JCL ancestor: {@code ParseError} (under
        {@code Exception}) beside {@code Boom} (under {@code RuntimeException})
        join at {@code java.lang.Exception} -- a class the user never wrote,
        and the likeliest wrong-join site. Catching exactly
        {@code java.lang.Exception} covers the carrier only if the join landed
        there. */
    static int jclJoinThrower(int x) throws ParseError, Boom {
        if (x < 0) {
            throw new ParseError();
        }
        if (x > 100) {
            throw new Boom();
        }
        return x;
    }

    static int jclJoinDispatch(int x) {
        try {
            return jclJoinThrower(x);
        } catch (Exception e) {
            return 1;
        }
    }

    /** One try with both catch arms AND a finalizer: the finalizer runs after
        the arm, on every path. */
    static int bothArmsAndFinally(boolean b) {
        postcondition((int r) -> (b && r == 11) || (!b && r == 12));
        int picked = 0;
        int ran = 0;
        try {
            if (b) {
                throw new ParseError();
            } else {
                throw new IoError();
            }
        } catch (ParseError e) {
            picked = 1;
        } catch (IoError e) {
            picked = 2;
        } finally {
            ran = 10;
        }
        return picked + ran;
    }

    /** Re-throw from within a catch arm: the arm's own try does not license
        it (enclosing-catch scoping covers the try BODY only), so the method
        must declare the thrown type. */
    static int rethrowFromArm(int x) throws ParseError {
        postcondition((int r) -> r == x);
        try {
            if (x < -1000) {
                throw new ParseError();
            }
        } catch (ParseError e) {
            throw e;
        }
        return x;
    }

    /** return crossing a finally still runs the arm. */
    static int returnThroughFinally() {
        postcondition((int r) -> r == 1);
        int c = 0;
        try {
            return 1;
        } finally {
            c = c + 1;
            check(c == 1);
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
