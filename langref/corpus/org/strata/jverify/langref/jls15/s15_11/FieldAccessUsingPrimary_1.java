package org.strata.jverify.langref.jls15.s15_11;

import static org.strata.jverify.JVerify.*;

// JLS 15.11.1 -- Field Access Using a Primary: accessing an instance field
// through a Primary expression (new Point_FieldAccessUsingPrimary_1().x) and through a local reference.
class FieldAccessUsingPrimary_1 {
    static void run() {

        // Field access where the Primary is a class instance creation expression.
        int fromNew = new Point_FieldAccessUsingPrimary_1(3, 4).x;
        check(fromNew == 3);
    }

    static Point_FieldAccessUsingPrimary_1 make() {
        return new Point_FieldAccessUsingPrimary_1(1, 2);
    }
}

class Point_FieldAccessUsingPrimary_1 {
    int x;
    int y;

    Point_FieldAccessUsingPrimary_1(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
