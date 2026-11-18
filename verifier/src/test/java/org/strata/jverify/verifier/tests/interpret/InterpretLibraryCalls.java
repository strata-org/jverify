package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A call to a library method that no contract gives a body runs the method's
 * own bytecode: a static method; arithmetic that wraps in the library where
 * the program's overflow is an error; a method that writes an array's
 * elements; a constructor and a method that write the object's fields; and a
 * program class that extends a library class, inherits its fields and methods
 * and overrides one, which a virtual call on a receiver of the library class
 * then runs.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0,
        errorCount = 0)
class InterpretLibraryCalls {
    static class Counted extends java.awt.Point {
        int moves;

        Counted(int x, int y) {
            super(x, y);
        }

        @Override
        public void move(int x, int y) {
            moves++;
            super.move(x, y);
        }
    }

    static void statics() {
        check(Math.abs(-5) == 5);
        check(Math.max(3, 7) == 7);
        check(Math.min(3L, 7L) == 3L);
    }

    static void wraps() {
        check(Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE);
    }

    static void arrays() {
        int[] a = new int[3];
        java.util.Arrays.fill(a, 4);
        check(a[0] + a[1] + a[2] == 12);
    }

    static void fields() {
        java.awt.Point p = new java.awt.Point(3, 4);
        p.translate(1, 2);
        check(p.x == 4);
        check(p.y == 6);
    }

    static void subclass() {
        Counted c = new Counted(1, 2);
        c.translate(1, 1);
        check(c.x == 2 && c.y == 3);
        check(c.moves == 0);
        java.awt.Point p = c;
        p.move(7, 8);
        check(c.moves == 1);
        check(c.x == 7 && c.y == 8);
        new java.awt.Point(0, 0).move(1, 1);
        check(c.moves == 1);
    }

    @InterpretEntry
    static void run() {
        statics();
        wraps();
        arrays();
        fields();
        subclass();
    }
}
