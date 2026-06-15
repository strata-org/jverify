package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Regression tests for strata-org/jverify#443.
 *
 * <p>A valueless {@code return;} reaches {@code convertStatement} as a droppable (null)
 * statement, because Laurel's return op cannot express a valueless return (strata-org/Strata#1353).
 * Two node-bound sites used to pass that null straight into a Laurel node — the {@code if}
 * then-branch and a non-loop labelled body — producing a {@code NullPointerException} during
 * Laurel serialization. Both sites are now null-guarded with an empty block, mirroring the
 * existing else-branch guard.
 *
 * <p>These tests pin the no-crash behavior. Note the bare return is <em>dropped</em> rather than
 * modeled as control flow, so the methods below are written not to depend on the early return
 * actually short-circuiting; they verify cleanly under the (sound but incomplete) translation.
 */
@SuppressWarnings({"ConstantValue", "UnusedLabel"})
@JVerifyTest(methodsVerified = 4, errorCount = 0)
class VerifyBareReturn {

    /**
     * {@code if (cond) return;} at method level. The bare return is the then-branch,
     * which {@code convertStatement} drops; the then-branch must be null-guarded.
     * Previously NPE'd at L386 (ifThenElse with a null then-branch).
     */
    static void ifThenBareReturn() {
        int x = 5;
        if (x == 5) return;
        check(x == 5);
    }

    /**
     * Labeled bare return: a non-loop labelled statement whose body is a droppable
     * {@code return;}. The labelled body must be null-guarded.
     * Previously NPE'd at L474 (labelledBlock with a null body).
     */
    static void labeledBareReturn() {
        lbl:
        return;
    }

    /**
     * Bare return inside a (do-while) loop, reached through the loop's if-then branch.
     * The invariant bounds {@code x} across all paths the verifier sees (including the
     * one where the dropped return does not short-circuit).
     */
    static void bareReturnInsideLoop() {
        int x = 0;
        do {
            invariant(0 <= x && x <= 3);
            if (x == 2) return;
            x = x + 1;
        } while (x < 3);
    }
}
