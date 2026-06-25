package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Regression test for the empty-statement ({@code ;}) NPE in Java-to-Laurel
 * lowering. An empty statement converts to {@code null}; three positions used
 * to propagate that null into Laurel nodes the Ion serializer then
 * dereferenced (Node.toIon NPE): the if-then branch, a labeled-statement body,
 * and a non-block loop body ({@code for (..) ;}). Each is now a valid no-op.
 */
@SuppressWarnings({"StatementWithEmptyBody", "ConstantValue", "UnnecessaryLabelOnContinueStatement"})
@JVerifyTest(methodsVerified = 4, errorCount = 0)
class EmptyStatement {
    // if-then branch is an empty statement
    static void emptyIfBranch(int x) {
        if (x > 0)
            ;
        check(true);
    }

    // labeled-statement body is an empty statement
    static void emptyLabeledStatement() {
        label:
        ;
        check(true);
    }

    // non-block loop body is an empty statement
    static void emptyForBody() {
        // A boolean step avoids the integer-overflow proof obligation an
        // `i = i + 1` step would carry without a loop invariant — this test
        // only needs to exercise the non-block empty loop body lowering.
        for (boolean done = false; !done; done = true)
            ;
        check(true);
    }
}
