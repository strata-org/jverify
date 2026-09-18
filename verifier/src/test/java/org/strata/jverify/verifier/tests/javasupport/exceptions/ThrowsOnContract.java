package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.implies;
import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.throwsOn;

/**
 * Phase 4 of exception support (docs/exceptions-design.md, Phasing §4): the
 * exceptional contract DSL. {@code throwsOn(C)} opens an exceptional
 * behavior case whose condition <em>forces</em> the throw; the
 * case's clauses chain off it, reusing the normal-exit vocabulary scoped to
 * that throwing path ({@code .postcondition(...)} for the thrown value,
 * {@code .modifies(...)} for the case's frame). Stating cases claims the
 * enumeration is complete, so a caller that refutes every condition can
 * prove its handler dead, and {@code throwsOn(false)} states the method
 * never throws.
 */
@JVerifyTest(methodsVerified = 10) // 6 static methods + 4 implicit constructors
class ThrowsOnContract {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}
    static class IoError extends AppException {}

    /** The case's condition forces the throw and the body honours it.
        (The normal path returns a parameter unchanged: arithmetic would add
        an int32-overflow obligation, which is real but orthogonal to what
        this test pins.) */
    static int divide(int a, int b) throws AppException {
        throwsOn(b == 0);
        if (b == 0) {
            throw new AppException();
        }
        return a;
    }

    /** A per-type claim: the thrown value narrows below the declared type
        with instanceof (the case postcondition's parameter is typed at the
        declared throws type; several clauses in one chain conjoin). */
    static int parse(int x) throws AppException {
        throwsOn(x < 0).postcondition((AppException e) -> e instanceof ParseError);
        if (x < 0) {
            throw new ParseError();
        }
        return x;
    }

    /** Two cases, each forcing its own path, jointly exhaustive. */
    static int twoCases(int x) throws AppException {
        throwsOn(x < 0).postcondition((AppException e) -> e instanceof ParseError);
        throwsOn(x > 100).postcondition((AppException e) -> e instanceof IoError);
        if (x < 0) {
            throw new ParseError();
        }
        if (x > 100) {
            throw new IoError();
        }
        return x;
    }

    /** throwsOn(false) = never throws: verifies
        because the body indeed cannot throw. */
    static int neverThrows(int x) throws AppException {
        throwsOn(false);
        return x;
    }

    /** Caller side, forwards: the callee's condition holds, so the throw is
        certain -- the normal path is dead and the handler's value is the
        only outcome. */
    static int callerKnowsItThrows() {
        postcondition((int r) -> r == 99);
        try {
            int unused = divide(5, 0);
            check(false);
            return -1;
        } catch (AppException e) {
            return 99;
        }
    }

    /** Caller side, backwards: every condition refuted, so the call cannot
        have thrown and the handler is dead. */
    static int handlerIsDead(int x) {
        int y = x < 0 ? 0 : (x > 100 ? 100 : x);
        try {
            return twoCases(y);
        } catch (AppException e) {
            check(false);
            return -1;
        }
    }
}
