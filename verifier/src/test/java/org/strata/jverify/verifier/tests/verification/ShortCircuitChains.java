package org.strata.jverify.verifier.tests.verification;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Long short-circuit chains, as written for character classes: 40 ranges
 * joined by {@code ||} and their complement joined by {@code &&}. javac
 * compiles each {@code (a && b) || rest} to jumps that reach {@code rest} from
 * two places; translating it once per arm doubled the Laurel program with
 * every range, so that 16 ranges took minutes to verify. The chains must
 * translate linearly, and {@code inRanges} and {@code outside} must be
 * complements.
 */
@JVerifyTest(exitCode = 0, errorCount = 0)
class ShortCircuitChains {
    @Transparent
    static boolean inRanges(int c) {
        return (c >= 0 && c <= 5) || (c >= 10 && c <= 15) || (c >= 20 && c <= 25) || (c >= 30 && c <= 35)
                || (c >= 40 && c <= 45) || (c >= 50 && c <= 55) || (c >= 60 && c <= 65)
                || (c >= 70 && c <= 75) || (c >= 80 && c <= 85) || (c >= 90 && c <= 95)
                || (c >= 100 && c <= 105) || (c >= 110 && c <= 115) || (c >= 120 && c <= 125)
                || (c >= 130 && c <= 135) || (c >= 140 && c <= 145) || (c >= 150 && c <= 155)
                || (c >= 160 && c <= 165) || (c >= 170 && c <= 175) || (c >= 180 && c <= 185)
                || (c >= 190 && c <= 195) || (c >= 200 && c <= 205) || (c >= 210 && c <= 215)
                || (c >= 220 && c <= 225) || (c >= 230 && c <= 235) || (c >= 240 && c <= 245)
                || (c >= 250 && c <= 255) || (c >= 260 && c <= 265) || (c >= 270 && c <= 275)
                || (c >= 280 && c <= 285) || (c >= 290 && c <= 295) || (c >= 300 && c <= 305)
                || (c >= 310 && c <= 315) || (c >= 320 && c <= 325) || (c >= 330 && c <= 335)
                || (c >= 340 && c <= 345) || (c >= 350 && c <= 355) || (c >= 360 && c <= 365)
                || (c >= 370 && c <= 375) || (c >= 380 && c <= 385) || (c >= 390 && c <= 395);
    }

    @Transparent
    static boolean outside(int c) {
        return (c < 0 || c > 5) && (c < 10 || c > 15) && (c < 20 || c > 25) && (c < 30 || c > 35)
                && (c < 40 || c > 45) && (c < 50 || c > 55) && (c < 60 || c > 65)
                && (c < 70 || c > 75) && (c < 80 || c > 85) && (c < 90 || c > 95)
                && (c < 100 || c > 105) && (c < 110 || c > 115) && (c < 120 || c > 125)
                && (c < 130 || c > 135) && (c < 140 || c > 145) && (c < 150 || c > 155)
                && (c < 160 || c > 165) && (c < 170 || c > 175) && (c < 180 || c > 185)
                && (c < 190 || c > 195) && (c < 200 || c > 205) && (c < 210 || c > 215)
                && (c < 220 || c > 225) && (c < 230 || c > 235) && (c < 240 || c > 245)
                && (c < 250 || c > 255) && (c < 260 || c > 265) && (c < 270 || c > 275)
                && (c < 280 || c > 285) && (c < 290 || c > 295) && (c < 300 || c > 305)
                && (c < 310 || c > 315) && (c < 320 || c > 325) && (c < 330 || c > 335)
                && (c < 340 || c > 345) && (c < 350 || c > 355) && (c < 360 || c > 365)
                && (c < 370 || c > 375) && (c < 380 || c > 385) && (c < 390 || c > 395);
    }

    /** The same comparison in both arms is two blocks in bytecode: nothing is shared, each arm keeps its copy. */
    @Transparent
    static boolean mixed(boolean a, boolean b, boolean d, int c) {
        return a ? (b || c > 7) : (d && c > 7);
    }

    static void complements(int c) {
        check(inRanges(12));
        check(!inRanges(7));
        check(outside(7));
        check(!outside(12));
        check(inRanges(c) != outside(c));
    }

    /** The chain decides an int-valued ternary: the shared block's value is an int, not a boolean. */
    @Transparent
    static int thenInt(int a, int b, int c, int d) {
        return ((a > 0 && b > 0) || c > 0) ? (d > 0 ? 2 : 1) : 0;
    }

    @Transparent
    static int elseInt(int a, int b, int c, int d) {
        return ((a > 0 && b > 0) || c > 0) ? 1 : (d > 0 ? 2 : 0);
    }

    @Transparent
    static int switchArm(int k, int a, int b, int c) {
        int r = switch (k) {
            case 1 -> ((a > 0 && b > 0) || c > 0) ? 1 : 0;
            default -> 5;
        };
        return r;
    }

    static void intConsumers(int d) {
        check(thenInt(1, 1, 0, 1) == 2);
        check(thenInt(1, 1, 0, 0) == 1);
        check(thenInt(1, 0, 1, 1) == 2);
        check(thenInt(1, 0, 0, 1) == 0);
        check(thenInt(0, 1, 1, 0) == 1);
        check(elseInt(1, 1, 0, 1) == 1);
        check(elseInt(0, 0, 0, 1) == 2);
        check(elseInt(0, 0, 0, 0) == 0);
        check(elseInt(0, 0, 1, d) == 1);
        check(switchArm(1, 1, 1, 0) == 1);
        check(switchArm(1, 0, 1, 0) == 0);
        check(switchArm(1, 0, 0, 1) == 1);
        check(switchArm(2, 0, 0, 0) == 5);
    }

    /** The chain decides a ternary with values other than 0 and 1, and an if statement. */
    @Transparent
    static int fiveOrSeven(int c) {
        return (c >= 0 && c <= 5) || (c >= 10 && c <= 15) || (c >= 20 && c <= 25) ? 5 : 7;
    }

    @Transparent
    static int ifStatement(int c) {
        if ((c >= 0 && c <= 5) || (c >= 10 && c <= 15) || (c >= 20 && c <= 25)) {
            return 5;
        }
        return 7;
    }

    @Transparent
    static int guarded(int c) {
        if (!((c >= 0 && c <= 5) || (c >= 10 && c <= 15) || (c >= 20 && c <= 25))) {
            c = -1;
        }
        return c;
    }

    static void otherConsumers(int c) {
        check(fiveOrSeven(12) == 5);
        check(fiveOrSeven(7) == 7);
        check(fiveOrSeven(c) == ifStatement(c));
        check(guarded(12) == 12);
        check(guarded(7) == -1);
        check((guarded(c) == -1) == (fiveOrSeven(c) == 7));
    }

    /** Operands after the first stay guarded by the earlier ones when the chain is an if condition. */
    static int firstPositive(int[] a, int i) {
        if (i >= 0 && i < a.length && a[i] > 0) {
            return a[i];
        }
        return -1;
    }

    /** An assert alone in an if body is a statement, not an operand of the condition. */
    static int assertInIf(int a, int b) {
        if (a > 0) {
            assert a > 0;
        }
        return a;
    }

    /** The division's ArithmeticException is caught: the operand keeps its explicit throw, so the catch fires. */
    static int caughtDivision(int x, int y) {
        postcondition((int res) -> (res == -1) == (x > 0 && y == 0)
                && (res == 1) == (x > 0 && y != 0 && x / y > 2)
                && (res == -1 || res == 0 || res == 1));
        int r = 0;
        try {
            if (x > 0 && x / y > 2) {
                r = 1;
            }
        } catch (ArithmeticException e) {
            r = -1;
        }
        return r;
    }

    @Transparent
    static int nestedIfs(int a, int b) {
        int r = 0;
        if (a > 0) {
            r = r + 2;
            if (b > 0) {
                r = 1;
            }
        }
        return r;
    }

    static void statementsAroundChains(int a, int b) {
        check(nestedIfs(1, 1) == 1);
        check(nestedIfs(1, 0) == 2);
        check(nestedIfs(0, 1) == 0);
    }

    /**
     * Operands that are calls with effects: each must run once, and only when
     * the bytecode runs it. The postconditions pin the count and the result.
     */
    static class Calls {
        int counter;

        Calls() {
            postcondition(counter == 0);
            counter = 0;
        }

        boolean f(int c) {
            precondition(0 <= counter && counter < 1000);
            modifies(this);
            postcondition((boolean res) -> res == (c > 0) && counter == old(counter) + 1);
            counter = counter + 1;
            return c > 0;
        }

        boolean g(int c) {
            precondition(0 <= counter && counter < 1000);
            modifies(this);
            postcondition((boolean res) -> res == (c > 3) && counter == old(counter) + 1);
            counter = counter + 1;
            return c > 3;
        }

        boolean h(int c) {
            precondition(0 <= counter && counter < 1000);
            modifies(this);
            postcondition((boolean res) -> res == (c < -5) && counter == old(counter) + 1);
            counter = counter + 1;
            return c < -5;
        }

        boolean chain(int c) {
            precondition(0 <= counter && counter < 900);
            modifies(this);
            postcondition((boolean res) -> res == (c > 3 || c < -5));
            postcondition(counter == old(counter) + (c > 3 ? 2 : c > 0 ? 3 : 2));
            return (f(c) && g(c)) || h(c);
        }

        int chainIf(int c) {
            precondition(0 <= counter && counter < 900);
            modifies(this);
            postcondition((int res) -> (res == 0 || res == 1) && (res == 1) == (c > 3 || c < -5));
            postcondition(counter == old(counter) + (c > 3 ? 2 : c > 0 ? 3 : 2));
            if ((f(c) && g(c)) || h(c)) {
                return 1;
            }
            return 0;
        }

        /**
         * Three disjuncts, so the shared blocks are factored in two steps
         * and the statements the calls emit are re-attached across steps.
         * Calls per range of {@code c}: above 3, f g; 1 to 3, f g h g;
         * -5 to 0, f h g; below -5, f h f g.
         */
        boolean chain3(int c) {
            precondition(0 <= counter && counter < 900);
            modifies(this);
            postcondition((boolean res) -> res == (c > 3));
            postcondition(counter == old(counter) + (c > 3 ? 2 : c > 0 ? 4 : c >= -5 ? 3 : 4));
            return (f(c) && g(c)) || (h(c) && f(c)) || g(c);
        }

        int chainIf3(int c) {
            precondition(0 <= counter && counter < 900);
            modifies(this);
            postcondition((int res) -> (res == 0 || res == 1) && (res == 1) == (c > 3));
            postcondition(counter == old(counter) + (c > 3 ? 2 : c > 0 ? 4 : c >= -5 ? 3 : 4));
            if ((f(c) && g(c)) || (h(c) && f(c)) || g(c)) {
                return 1;
            }
            return 0;
        }
    }

    static void callOperands() {
        Calls k = new Calls();
        check(k.chain(5));
        check(k.counter == 2);
        check(!k.chain(1));
        check(k.counter == 5);
        check(k.chain(-10));
        check(k.counter == 7);
        check(k.chainIf(-1) == 0);
        check(k.counter == 9);
        check(k.chainIf(4) == 1);
        check(k.counter == 11);
    }

    static void callOperandsThreeDisjuncts() {
        Calls k = new Calls();
        check(k.chain3(5));
        check(k.counter == 2);
        check(!k.chain3(2));
        check(k.counter == 6);
        check(!k.chain3(-3));
        check(k.counter == 9);
        check(!k.chain3(-10));
        check(k.counter == 13);
        check(k.chainIf3(5) == 1);
        check(k.counter == 15);
        check(k.chainIf3(2) == 0);
        check(k.counter == 19);
        check(k.chainIf3(-3) == 0);
        check(k.counter == 22);
        check(k.chainIf3(-10) == 0);
        check(k.counter == 26);
    }

    /** A nested int ternary as an operand. */
    @Transparent
    static boolean ternaryOperand(int a, int b, int x, int y, int c) {
        return (a > 0 && (b > 0 ? x : y) > 0) || c > 7;
    }

    static void ternaryOperands(int c) {
        check(ternaryOperand(1, 1, 5, -5, 0));
        check(!ternaryOperand(1, 0, 5, -5, 0));
        check(ternaryOperand(1, 0, 5, -5, 8));
        check(!ternaryOperand(0, 1, 5, -5, 7));
        check(ternaryOperand(0, 1, 5, -5, c) == (c > 7));
    }

    static void mixedPolarity(int c) {
        check(mixed(true, true, false, 0));
        check(mixed(true, false, false, 8));
        check(!mixed(true, false, false, 7));
        check(mixed(false, false, true, 8));
        check(!mixed(false, false, true, 7));
        check(!mixed(false, true, false, 8));
        check(mixed(true, false, true, c) == (c > 7));
    }
}
