package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.everything;
import static org.strata.jverify.JVerify.implies;
import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.throwsOn;

/**
 * Phase 4 of exception support: the
 * exceptional contract DSL. {@code throwsOn(C)} opens an exceptional
 * behavior case whose condition <em>forces</em> the throw; the
 * case's clauses chain off it, reusing the normal-exit vocabulary scoped to
 * that throwing path ({@code .postcondition(...)} for the thrown value,
 * {@code .modifies(...)} for the case's frame). Stating cases claims the
 * enumeration is complete, so a caller that refutes every condition can
 * prove its handler dead, and {@code throwsOn(false)} states the method
 * never throws.
 */
@JVerifyTest(methodsVerified = 19) // 14 static methods + 5 implicit constructors
class ThrowsOnContract {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}
    static class IoError extends AppException {}
    static class Cell {
        int val;
    }

    /** A read of Cell.val: field registration is read-driven today (a
        write-only field fails resolution -- pre-existing, tracked
        separately), so the case-frame fixtures below need one reader. */
    static boolean readCell(Cell c) {
        return c.val > 0;
    }

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

    /** The headline chain end-to-end: guard, per-type claim, and case frame
        in one statement. The throwing path writes the cell and the case
        frame names it, giving callers the per-path precision an unstated
        frame would not (see unframedCaseByDefault). */
    static int chainWithFrame(Cell log, int x) throws AppException {
        throwsOn(x < 0)
                .postcondition((AppException e) -> e instanceof ParseError)
                .modifies(log);
        if (x < 0) {
            log.val = 1;
            throw new ParseError();
        }
        return x;
    }

    /** everything() as a case frame entry, same argument forms as the
        normal-exit modifies. */
    static int caseFrameEverything(Cell log, int x) throws AppException {
        throwsOn(x < 0).modifies(everything());
        if (x < 0) {
            log.val = 1;
            throw new AppException();
        }
        return x;
    }

    /** Multi-link chain: several postcondition clauses in one case conjoin,
        beside its frame, in one statement. */
    static int multiLink(Cell log, int x) throws AppException {
        throwsOn(x < 0)
                .postcondition((AppException e) -> e instanceof ParseError)
                .postcondition((AppException e) -> !(e instanceof IoError))
                .modifies(log);
        if (x < 0) {
            log.val = 1;
            throw new ParseError();
        }
        return x;
    }

    /** A flat normal-exit postcondition and a chained case coexist in one
        contract: the flat clause keeps normal-exit semantics, the chained
        clause stays scoped to its case -- exactly the disambiguation the
        chain-before-flat dispatch protects. */
    static int flatAndChained(int x) throws AppException {
        postcondition((int r) -> r >= 0);
        throwsOn(x < 0).postcondition((AppException e) -> e instanceof ParseError);
        if (x < 0) {
            throw new ParseError();
        }
        return x;
    }

    /** Under a multi-throws join the case postcondition's parameter is typed
        at the JOIN ({@code AppException}), which neither declared type
        spells -- the accept side of the boundary ThrowsOnSkips refuses. */
    static int joinParam(int x) throws ParseError, IoError {
        throwsOn(x < 0).postcondition((AppException e) -> e instanceof ParseError);
        if (x < 0) {
            throw new ParseError();
        }
        return x;
    }

    /** A case with NO stated frame leaves its throwing path unframed: the
        heap write below verifies without a .modifies clause. (Unlike the
        normal path, whose unstated frame is the "nothing changes" default
        plus the heap-write wildcard fallback.) State a case frame when
        callers need to know what survives the throw. */
    static int unframedCaseByDefault(Cell log, int x) throws AppException {
        throwsOn(x < 0);
        if (x < 0) {
            log.val = 1;
            throw new AppException();
        }
        return x;
    }

    /** A @Pure method with an exceptional contract and no normal-exit spec:
        the body may stay Transparent -- cases ride on `Procedure.throwsOn`,
        not on the body, and are checked regardless of body kind. (Confirmed
        in the failing direction too: the same shape with a false
        `throwsOn(false)` fails the exhaustiveness claim; not pinned here
        because that diagnostic is currently unattributed to a method.) */
    @org.strata.jverify.Pure
    static int pureWithCase(int x) throws AppException {
        throwsOn(x < 0);
        if (x < 0) {
            throw new AppException();
        }
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
