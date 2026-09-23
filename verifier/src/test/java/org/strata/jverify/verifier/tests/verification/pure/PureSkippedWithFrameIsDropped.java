package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Impure;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

/**
 * A SKIPPED {@code @Pure} method that holds a real frame is DROPPED, not reduced to an
 * opaque declaration.
 *
 * <p>{@code JavaToLaurelCompiler.declarationOnly} installs an EMPTY frame, which is a
 * positive promise that the call changes nothing. That is only honest for a procedure
 * whose frame is already empty -- and purity does not imply it: a {@code @Pure} method
 * may write the heap, and the front end then synthesizes a wildcard {@code modifies *}
 * for it. {@code keepsOpaqueDeclaration} is the gate; when it says no, the procedure is
 * dropped and its callers cascade-demote, exactly as they already do for a non-pure
 * callee.
 *
 * <p>{@code accumulate} is the shape that makes the difference observable. It is a legal
 * pure block (one declaration, then a return), so it translates; its initializer calls a
 * non-pure method, which is what makes {@code bodyPotentiallyWritesHeap} true and gives
 * it {@code modifies *}; and that same callee is refused, which cascade-demotes it. The
 * third error is the point: without the frame gate {@code accumulate}'s declaration
 * claimed an empty frame and {@code caller} PROVED {@code b.f == 0} across a call that
 * may write {@code b.f}.
 *
 * <p>Compare {@code PureUnverifiedMethod}, whose pure callee has no frame and so keeps
 * its declaration and stays callable.
 */
@JVerifyTest(exitCode = 2)
public class PureSkippedWithFrameIsDropped {

    @Impure
    static class Box {
        int f;
    }

    static class Helper {
        int step(Box b) {
            return 1;
        }
    }

    static int helper(Box b) {
//             ^ error: method call on a freshly-allocated receiver is not yet supported
        return new Helper().step(b);
    }

    @Pure
    static int accumulate(Box b) {
//             ^ error: call to 'helper', which could not be translated
        int stepped = helper(b);
        return stepped;
    }

    static void caller(Box b) {
//              ^ error: call to 'accumulate', which could not be translated: call to 'helper', which could not be translated
        precondition(b.f == 0);
        var r = accumulate(b);
        check(b.f == 0);
    }
}
