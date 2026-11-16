package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * In a program that boxes, {@code ==} and {@code !=} on operands that cannot
 * hold a box (an object of a program class, an enum constant, a string, an
 * array, on every path into the comparison) are reference comparisons as in a
 * program that does not box.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0,
        methodsVerified = 8,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretBoxingReferenceComparison {
    static class Point {
    }

    enum Color { RED, GREEN }

    static boolean same(Point a, Point b) {
        return a == b;
    }

    static boolean sameArray(int[] a, int[] b) {
        return a == b;
    }

    static boolean flag(boolean b) {
        return b;
    }

    @InterpretEntry
    static void run() {
        Integer boxed = 1;
        Boolean truth = true;
        check(boxed.equals(1) && truth.equals(true));

        Point p = new Point();
        Point q = p;
        Point r = new Point();
        check(p == q);
        check(p != r);
        check(same(p, q) && !same(p, r));

        Object held = boxed;
        check(p != held);

        Color c = Color.RED;
        Color d = Color.GREEN;
        check(c != d);

        String s = "a";
        String t = "a";
        check(s == t);

        int[] xs = new int[1];
        int[] ys = xs;
        int[] zs = new int[1];
        check(xs == ys);
        check(xs != zs);
        check(sameArray(xs, ys) && !sameArray(xs, zs));

        Object point = r;
        check(point == r);
        check((flag(true) ? p : r) == q);
        Point chosen = flag(false) ? p : r;
        check(chosen == r);
    }
}
