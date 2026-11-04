package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A nested loop as a switch arm is a labelled block (the inner loop),
 * unwrapped from its single-statement arm: the OUTER loop's back edge must
 * still run after it, inside the arm. With that back edge dropped, the outer
 * loop would exit after one pass through the arm, under-approximating its
 * iterations -- which once let the false {@code j == 0} invariants below
 * VERIFY. All three must be refuted ({@code j} reaches 3 / {@code x} is
 * written to 3).
 */
@JVerifyTest(exitCode = 4, methodsVerified = 1, methodsInvalid = 3, errorCount = 3)
class SwitchArmNestedLoop {

    /** The nested loop in {@code case 0}. */
    static int loopInCaseArm(int n) {
        precondition(n == 5);
        int i = 0;
        int j = 0;
        int x = 0;
        while (i < n) {
            invariant(0 <= i && i <= n && j == 0 && 0 <= x && x <= i);
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            i = i + 1;
            switch (i % 2) {
                case 0:
                    while (j < 3) {
                        invariant(0 <= j && j <= 3);
                        j = j + 1;
                    }
                    break;
                default:
                    x = x + 1;
            }
        }
        return x;
    }

    /** The nested loop in the {@code default} arm. */
    static int loopInDefaultArm(int n) {
        precondition(n == 5);
        int i = 0;
        int j = 0;
        int x = 0;
        while (i < n) {
            invariant(0 <= i && i <= n && j == 0 && 0 <= x && x <= i);
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            i = i + 1;
            switch (i % 2) {
                case 0:
                    x = x + 1;
                    break;
                default:
                    while (j < 3) {
                        invariant(0 <= j && j <= 3);
                        j = j + 1;
                    }
            }
        }
        return x;
    }

    /** Control: a plain-statement arm was never affected and stays refuted. */
    static int plainStatementArm(int n) {
        precondition(n == 5);
        int i = 0;
        int j = 0;
        int x = 0;
        while (i < n) {
            invariant(0 <= i && i <= n && j == 0 && 0 <= x && x <= i);
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            i = i + 1;
            switch (i % 2) {
                case 0:
                    j = 3;
                    break;
                default:
                    x = x + 1;
            }
        }
        return x;
    }
}
