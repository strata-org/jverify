package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Mirrors Strata's T23_IncrDecr.lean test cases (local variable forms).
 */
@JVerifyTest(exitCode = 0)
class IncrementDecrement {
    // --- Statement form ---

    static void stmtForm() {
        int x = 5;
        x++;
        check(x == 6);
        x--;
        check(x == 5);
        ++x;
        check(x == 6);
        --x;
        check(x == 5);
    }

    // --- Expression position: prefix yields new value ---

    static void preIncrYieldsNew() {
        int x = 0;
        int y = ++x;
        check(x == 1);
        check(y == 1);
    }

    // --- Expression position: postfix yields old value ---

    static void postIncrYieldsOld() {
        int x = 0;
        int y = x++;
        check(x == 1);
        check(y == 0);
    }

    // --- Repeated postfix: (x++)+(x++) ---

    static void repeatedPostIncr() {
        int x = 0;
        // Java semantics: first x++ yields 0 (x becomes 1),
        // second x++ yields 1 (x becomes 2), sum = 1.
        int y = x++ + x++;
        check(x == 2);
        check(y == 1);
    }

    // --- Repeated prefix: (++x)+(++x) ---

    static void repeatedPreIncr() {
        int x = 0;
        // Java semantics: first ++x yields 1 (x becomes 1),
        // second ++x yields 2 (x becomes 2), sum = 3.
        int y = ++x + ++x;
        check(x == 2);
        check(y == 3);
    }

    // --- Mixed prefix and postfix ---

    static void mixedPrePostIncr() {
        int x = 10;
        // x++ yields 10 (old), x becomes 11;
        // ++x yields 12 (new), x becomes 12; sum = 22.
        int y = x++ + ++x;
        check(x == 12);
        check(y == 22);
    }

    // --- Decrement ---

    static void preDecrYieldsNew() {
        int x = 5;
        int y = --x;
        check(x == 4);
        check(y == 4);
    }

    static void postDecrYieldsOld() {
        int x = 5;
        int y = x--;
        check(x == 4);
        check(y == 5);
    }

    // --- For-loop step ---

    static void forLoopStep() {
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            invariant(0 <= i && i <= 3);
            invariant(sum == i);
            sum = sum + 1;
        }
        check(sum == 3);
    }

    // --- Increment in if condition ---

    static void postIncrementInIfCondition() {
        int x = 0;
        boolean hitThen = false;
        boolean hitElse = false;
        // x++ yields 0 (old), so 0 > 0 is false → else branch. x becomes 1.
        if (x++ > 0) {
            hitThen = true;
        } else {
            hitElse = true;
        }
        check(hitElse);
        check(!hitThen);
        check(x == 1);
    }

    static void preIncrementInIfCondition() {
        int x = 0;
        boolean hitThen = false;
        boolean hitElse = false;
        // ++x yields 1 (new), so 1 > 0 is true → then branch. x becomes 1.
        if (++x > 0) {
            hitThen = true;
        } else {
            hitElse = true;
        }
        check(hitThen);
        check(!hitElse);
        check(x == 1);
    }

    // --- Short-circuit with increment ---

    static void incrementInShortCircuitTaken() {
        int y = 10;
        // y > 0 is true → RHS evaluated: y++ yields 10, 10 > 5 is true, y becomes 11.
        boolean b = y > 0 && y++ > 5;
        check(b);
        check(y == 11);
    }

    static void incrementInShortCircuitSkipped() {
        int x = 0;
        int y = 0;
        // x > 0 is false → short-circuit: y++ NOT evaluated.
        boolean b = x > 0 && y++ > 5;
        check(!b);
        check(y == 0);
    }

    // --- Increment as function argument ---

    static int doubleIt(int n) {
        precondition(n >= 0 && n <= 100);
        return n * 2;
    }

    static void postIncrementAsFunctionArgument() {
        int x = 3;
        // doubleIt(x++) passes the OLD x (3); x becomes 4 after.
        int r = doubleIt(x++);
        check(x == 4);
    }

    static void preIncrementAsFunctionArgument() {
        int x = 3;
        // doubleIt(++x) passes the NEW x (4); x becomes 4 after.
        int r = doubleIt(++x);
        check(x == 4);
    }
}
