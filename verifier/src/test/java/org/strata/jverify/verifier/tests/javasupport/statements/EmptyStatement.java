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
    void emptyIfBranch(int x) {
        if (x > 0)
            ;
        check(true);
    }

    // labeled-statement body is an empty statement
    void emptyLabeledStatement() {
        label:
        ;
        check(true);
    }

    // non-block loop body is an empty statement
    void emptyForBody() {
        int i = 0;
        for (i = 0; i < 5; i = i + 1)
            ;
        check(i == 5);
    }
}
