package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "ConstantValue"})
@JVerifyTest(exitCode = 4, methodsVerified = 16, methodsInvalid = 1, errorCount = 1)
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

    static void multipleLoopsWithBreakAndContinue() {
        int a = 0;
        for (int i = 0; i < 5; i = i + 1) {
            invariant(i >= 0 && i <= 5);
            invariant(i <= 3 ? a == i : a == i - 1);
            if (i == 3) {
                continue;
            }
            a = a + 1;
        }
        check(a == 4);

        int b = 0;
        for (int j = 0; j < 5; j = j + 1) {
            invariant(j >= 0 && j <= 5);
            invariant(j <= 2 ? b == j : b == j - 1);
            if (j == 2) {
                continue;
            }
            b = b + 1;
        }
        check(b == 4);
    }

    static void breakAndContinueInSameLoop() {
        int x = 0;
        for (int i = 0; i < 10; i = i + 1) {
            invariant(i >= 0 && i <= 7);
            invariant(i <= 3 ? x == i * (i - 1) / 2 : x == i * (i - 1) / 2 - 3);
            if (i == 3) {
                continue;
            }
            if (i == 7) {
                break;
            }
            x = x + i;
        }
        check(x == 18);
    }

    static void labeledBreakOuter() {
        int x = 0;
        int y = 0;
        outerLoop:
        for (x = 0; x < 10; x = x + 1) {
            invariant(x >= 0 && x <= 10);
            invariant(y >= 0 && y <= 3);
            y = 0;
            while (y < 3) {
                invariant(y >= 0 && y <= 3);
                invariant(x >= 0 && x <= 10);
                if (x == 5 && y == 2) {
                    break outerLoop;
                }
                y = y + 1;
            }
        }
        check(x >= 0 && x <= 10);
        check(y >= 0 && y <= 3);
    }

    static void doWhileWithBreak() {
        int x = 0;
        do {
            invariant(x >= 0 && x <= 3);
            if (x == 3) {
                break;
            }
            x = x + 1;
        } while (x < 10);
        check(x == 3);
    }

    static boolean returnInsideLoop() {
	postcondition((boolean b) -> b == true);
        for (int i = 0; i < 10; i = i + 1) {
            invariant(i >= 0 && i <= 5);
            if (i == 5) {
                return true;
            }
        }
        return false;
    }

    static void doWhileWithContinue() {
        int x = 0;
        int sum = 0;
        do {
            invariant(x >= 0 && x <= 5);
            invariant(x <= 2 ? sum == x : sum == 2);
            if (x >= 2) {
                x = x + 1;
                continue;
            }
            sum = sum + 1;
            x = x + 1;
        } while (x < 5);
        check(sum == 2);
    }

    static void doWhileNumericInvariant() {
        // Bug 2 repro: with sentinel approach, invariant(0 <= x && x < 100) is unprovable
        // because __first=true weakens the guard. With body duplication, preservation is:
        // (0 <= x && x < 100) ∧ (x < 5) → (0 <= x+1 && x+1 < 100) ✓
        int x = 0;
        do {
            invariant(0 <= x && x < 100);
            x = x + 1;
        } while (x < 5);
        check(x >= 5);
    }

    static void breakBad() {
        int i = 0;
        for (i = 0; i < 5; i = i + 1) {
            invariant(i >= 0 && i <= 5);
            if (i == 5) {
                break;
            }
        }
        check(i == 6);
//      ^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
