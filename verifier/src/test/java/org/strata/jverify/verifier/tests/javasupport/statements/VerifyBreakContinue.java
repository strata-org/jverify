package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "ConstantValue"})
@JVerifyTest(methodsVerified = 9, errorCount = 0)
class VerifyBreakContinue {
    static void forLoopBreak() {
        int i = 0;
        for (i = 0; i < 10; i = i + 1) {
            invariant(i >= 0 && i <= 5);
            if (i == 5) {
                break;
            }
        }
        check(i == 5);
    }

    static void whileWithBreak() {
        var x = 0;
        while (x < 10) {
            invariant(x >= 0 && x <= 5);
            if (x == 5) {
                break;
            }
            x = x + 1;
        }
        check(x == 5);
    }

    static void whileInfiniteBreak() {
        var x = 0;
        while (true) {
            invariant(x >= 0 && x <= 3);
            if (x == 3) {
                break;
            }
            x = x + 1;
        }
        check(x == 3);
    }

    static void nestedBreakInner() {
        int total = 0;
        for (int i = 0; i < 3; i = i + 1) {
            invariant(i >= 0 && i <= 3);
            invariant(total == i * 2);
            int j = 0;
            for (j = 0; j < 5; j = j + 1) {
                invariant(j >= 0 && j <= 2);
                if (j == 2) {
                    break;
                }
            }
            check(j == 2);
            total = total + j;
        }
        check(total == 6);
    }

    static void forLoopContinue() {
        int i = 0;
        int x = 0;
        for (i = 0; i < 5; i = i + 1) {
            invariant(i >= 0 && i <= 5);
            invariant(i <= 2 ? x == i : x == 2);
            if (i >= 2) {
                continue;
            }
            x = x + 1;
        }
        check(x == 2);
    }

    static void forLoopContinueSingleSkip() {
        int i = 0;
        int x = 0;
        for (i = 0; i < 5; i = i + 1) {
            invariant(i >= 0 && i <= 5);
            invariant(i <= 2 ? x == i * (i - 1) / 2 : x == i * (i - 1) / 2 - 2);
            if (i == 2) {
                continue;
            }
            x = x + i;
        }
        check(x == 8);
    }

    static void whileWithContinue() {
        var i = 0;
        var x = 0;
        while (i < 5) {
            invariant(i >= 0 && i <= 5);
            invariant(i <= 2 ? x == i : x == 2);
            if (i >= 2) {
                i = i + 1;
                continue;
            }
            x = x + 1;
            i = i + 1;
        }
        check(x == 2);
    }

    static void whileWithContinueSingleSkip() {
        var i = 0;
        var x = 0;
        while (i < 5) {
            invariant(i >= 0 && i <= 5);
            invariant(i <= 2 ? x == i * (i - 1) / 2 : x == i * (i - 1) / 2 - 2);
            if (i == 2) {
                i = i + 1;
                continue;
            }
            x = x + i;
            i = i + 1;
        }
        check(x == 8);
    }
}
