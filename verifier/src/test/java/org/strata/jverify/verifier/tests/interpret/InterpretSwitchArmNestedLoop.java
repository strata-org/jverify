package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Concrete execution of the nested-loop-in-a-switch-arm shape: with the
 * outer back edge in place the loop runs all 5 iterations ({@code x}
 * counts the odd ones, {@code j} saturates at 3); losing the back edge
 * after the {@code case 0} arm would stop it early and fail the checks.
 */
@JVerifyTest(
        interpret = true,
        entry = "org.strata.jverify.verifier.tests.interpret.InterpretSwitchArmNestedLoop#run",
        exitCode = 0)
class InterpretSwitchArmNestedLoop {
    @InterpretEntry
    static void run() {
        int n = 5;
        int i = 0;
        int j = 0;
        int x = 0;
        while (i < n) {
            i = i + 1;
            switch (i % 2) {
                case 0:
                    while (j < 3) {
                        j = j + 1;
                    }
                    break;
                default:
                    x = x + 1;
            }
        }
        check(i == 5);
        check(x == 3);
        check(j == 3);
    }
}
