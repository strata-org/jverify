package org.strata.jverify.verifier.tests.javasupport.boxing;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Boxes when the program runs, for what no language reference program runs:
 * {@code equals} between boxes of one class, of two classes and on
 * {@code Boolean}; a {@code Short}; the box of the smallest {@code int};
 * {@code ++}, {@code +=} and {@code + 1} on a box, which unbox and box again;
 * a box as an {@code Object} is a {@code Number} and no {@code Long}; a box
 * kept in an instance field and in a static field; and a box made by the
 * wrapper's constructor. In this program that boxes, {@code ==} and
 * {@code !=} on operands that cannot hold a box (an object of a program class,
 * an enum constant, a string, an array, on every path into the comparison) are
 * reference comparisons as in a program that does not box. The Laurel route refuses {@code instanceof Number}
 * on a box and the use of a box read from a field, so the program runs on
 * the direct Cortex translation only.
 */
@JVerifyTest(interpret = true, entry = "org.strata.jverify.verifier.tests.javasupport.boxing.BoxingExecution#run",
        expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER},
        exitCode = 0, methodsInvalid = 0, methodsSkipped = 0, errorCount = 0)
class BoxingExecution {
    static class Holder {
        Integer count;
    }

    static Character initial;

    static void equalsOfOneClass() {
        Integer i = 43;
        Integer sum = 43;
        check(i.equals(sum));
        check(!i.equals(Integer.valueOf(44)));
        Long big = 43L;
        check(big.equals(43L));
    }

    static void equalsOfTwoClasses() {
        Integer i = 43;
        Long big = 43L;
        check(!i.equals(big));
        check(!big.equals(i));
    }

    static void equalsOnBoolean() {
        Boolean yes = true;
        Boolean no = false;
        check(yes.equals(true));
        check(!yes.equals(no));
    }

    static void shortAndSmallestInt() {
        Short s = (short) -32768;
        check(s == -32768);
        check(Integer.valueOf(-2147483648).intValue() == -2147483648);
    }

    static void arithmeticOnABox() {
        Integer i = 42;
        Integer sum = i + 1;
        check(sum == 43);
        i++;
        check(i == 43);
        Integer total = 0;
        for (int k = 0; k < 5; k++) {
            total += k;
        }
        check(total == 10);
    }

    static void boxAsObject() {
        Integer i = 43;
        Object o = i;
        check(o instanceof Number);
        check(!(o instanceof Long));
    }

    static void boxInFields() {
        Holder h = new Holder();
        h.count = 3;
        h.count = h.count + 1;
        check(h.count == 4);
        check(h.count.equals(4));
        initial = 'k';
        check(initial == 'k');
    }

    @SuppressWarnings("removal")
    static void wrapperConstructor() {
        Integer made = new Integer(5);
        check(made == 5);
        check(made.equals(5));
    }

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

    static void referenceComparisonsWithoutABox() {
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

    static void run() {
        referenceComparisonsWithoutABox();
        equalsOfOneClass();
        equalsOfTwoClasses();
        equalsOnBoolean();
        shortAndSmallestInt();
        arithmeticOnABox();
        boxAsObject();
        boxInFields();
        wrapperConstructor();
    }
}
