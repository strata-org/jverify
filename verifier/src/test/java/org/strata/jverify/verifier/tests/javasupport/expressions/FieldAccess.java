package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(methodsVerified = 14, errorCount = 2, exitCode = 4)
class FieldAccess {
    static class Point {
        int x;
        int y;
    }

    static class Line {
        Point start;
        Point end;
    }

    static void writeField() {
        Point p = new Point();
        p.x = 42;
        check(p.x == 42);
    }

    // `could not be proved` rather than `does not hold`, even though the value is
    // concrete. A Java `int` field maps to a constrained range type, and Strata's
    // constrained-type elimination attaches a quantified range axiom to every read of
    // one, which is enough to move this query from refuted to unknown. The assertion
    // still fails; only the wording is weaker.
    static void writeFieldKO() {
        Point p = new Point();
        p.x = 42;
        check(p.x == 41);
//      ^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void writeTwoFields() {
        Point p = new Point();
        p.x = 1;
        p.y = 2;
        check(p.x == 1);
        check(p.y == 2);
    }

    static void writeSameFieldTwice() {
        Point p = new Point();
        p.x = 1;
        p.x = 2;
        check(p.x == 2);
    }

    static void readFieldInBinary() {
        Point p = new Point();
        p.x = 3;
        p.y = 4;
        int z = p.x + p.y;
        check(z == 7);
    }

    static void readFieldInBinaryKO() {
        Point p = new Point();
        p.x = 3;
        p.y = 4;
        int z = p.x + p.y;
        check(z == 8);
//      ^^^^^^^^^^^^^ Error: assertion could not be proved
    }

    static void writesToArg(Point p) {
        p.x = 99;
    }

    static void readAndWriteInSameMethod() {
        Point p = new Point();
        p.x = 10;
        int y = p.x;
        p.x = y + 1;
        check(p.x == 11);
    }

    static void chainedRead() {
        Line l = new Line();
        Point p = new Point();
        p.x = 7;
        l.start = p;
        check(l.start.x == 7);
    }

    static void chainedWrite() {
        Line l = new Line();
        l.start = new Point();
        l.start.x = 5;
        check(l.start.x == 5);
    }

    static void writeInIfBranch(Point p, boolean b) {
        if (b) {
            p.x = 1;
        } else {
            p.x = 2;
        }
        check(p.x == 1 || p.x == 2);
    }

    static void fieldsAreDistinct() {
        Point p = new Point();
        p.x = 10;
        p.y = 20;
        // Writing to y must not disturb x.
        check(p.x == 10);
        check(p.y == 20);
    }

    static void aliasedRead() {
        Point p = new Point();
        Point q = p;
        p.x = 5;
        // p and q alias, so a write to p.x is visible via q.x.
        check(q.x == 5);
    }
}
