package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
// These loop methods fail to verify because of a Strata-side LoopElim
// limitation that is independent of the static-vs-instance encoding (confirmed
// by making a method static and seeing the same failures):
//
//   * nestedForLoop / nestedForLoopContinue / doWhileLoop carry an invariant()
//     that fails to verify as established/maintained (the diagnostic even
//     points at the loop rather than the invariant).
//   * forLoop has no invariant, so loop elimination havocs the counter and the
//     post-loop check(i == 5) becomes unprovable.
//
// The whole class is skipped (the engine short-circuits on skip), so its counts
// are intentionally NOT asserted. Re-enable and split out forLoop from the
// invariant-bearing methods when the Strata loop-invariant handling lands.
@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, errorCount = 4, methodsInvalid = 4)
class VerifyStatementsLoops {
    void forLoop() {
        int i = 0;
        for(i = 0; i < 5; i = i + 1) {
        }
        check(i == 5);
    }

    void nestedForLoop() {
        int x = 0;
        for (int i = 0; i < 5; i = i + 1) {
            invariant(x == i * 5);
            for (int j = 0; j < 5; j = j + 1) {
                invariant(x == j + i * 5);
                x = x + 1;
            }
        }
        check(x == 25);
    }

    void nestedForLoopContinue() {
        int x = 0;
        outerLoop:
        for (int i = 0; i < 5; i = i + 1) {
            invariant(i <= 2 ? x == i * 5 : x == 12 + (i - 3) * 5);

            for (int j = 0; j < 5; j = j + 1) {
                invariant(i <= 2 ? x == j + i * 5 : x == j + 12 + (i - 3) * 5);
                invariant(i == 2 ? j <= 2 : true);
                if (i == 2 && j == 2) {
                    check(x == 12);
                    continue outerLoop;
                }
                x = x + 1;
            }
        }
    }

    void doWhileLoop() {
        int x = 0;
        do {
            decreases(5 - x);
            invariant(x <= 5);
            x = x + 1;
        } while(x < 5);
        check(x == 5);
    }
}
