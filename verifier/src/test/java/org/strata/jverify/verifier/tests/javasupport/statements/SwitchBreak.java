package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.invariant;
import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;

/**
 * Regression tests for D566037730: a {@code break} in an arrow-case body
 * leaves the switch only (JLS 14.15). A verifier that resolves the break to
 * the enclosing loop checks a loop that exits earlier than the Java loop:
 * switchBreakStaysInLoop fails and switchBreakBad verifies.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 5, methodsInvalid = 1, errorCount = 1)
class SwitchBreak {
    static void switchBreakStaysInLoop() {
        int count = 0;
        int i = 0;
        while (i < 4) {
            invariant(0 <= i && i <= 4 && count == i);
            switch (i) {
                case 2 -> { break; } // exits the switch, not the loop
                default -> { }
            }
            count = count + 1;
            i = i + 1;
        }
        check(count == 4);
    }

    static int switchBreakBad(int n) {
        precondition(0 <= n && n <= 1000);
        postcondition((int r) -> r == n);
        int i = 0;
        while (i < 2 * n) {
            invariant(0 <= i && i <= n);
//                    ^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            switch (n - i) {
                case 0 -> { break; } // exits the switch; the loop runs to 2*n
                default -> { }
            }
            i = i + 1;
        }
        return i;
    }

    static void labeledBreakFromSwitchCase() {
        int i = 0;
        outer:
        while (i < 10) {
            invariant(0 <= i && i <= 3);
            switch (i) {
                case 3 -> { break outer; } // targets the loop, not the switch
                default -> { }
            }
            i = i + 1;
        }
        check(i == 3);
    }

    static void nestedSwitchBreak(int i) {
        precondition(0 <= i && i <= 5);
        int r = 0;
        int k = 0;
        while (k < 2) {
            invariant(0 <= k && k <= 2 && r == k);
            switch (i) {
                case 0 -> {
                    switch (k) {
                        case 0 -> { break; } // exits the inner switch only
                        default -> { }
                    }
                    r = r + 1;
                }
                default -> r = r + 1;
            }
            k = k + 1;
        }
        check(r == 2);
    }

    static void loopBreakInsideCase(int i) {
        int j = 0;
        switch (i) {
            case 0 -> {
                while (j < 10) {
                    invariant(0 <= j && j <= 2);
                    if (j == 2) break; // targets the while, not the switch
                    j = j + 1;
                }
            }
            default -> j = 2;
        }
        check(j == 2);
    }
}
