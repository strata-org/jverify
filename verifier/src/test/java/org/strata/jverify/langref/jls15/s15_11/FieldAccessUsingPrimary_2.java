package org.strata.jverify.langref.jls15.s15_11;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.11.1 -- Field Access Using a Primary: accessing an instance field
// through a Primary expression (new Point_FieldAccessUsingPrimary_2().x) and through a local reference.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_11.FieldAccessUsingPrimary_2#run")
class FieldAccessUsingPrimary_2 {
    static void run() {

        // Field access where the Primary is a local-variable reference.
        Point_FieldAccessUsingPrimary_2 p = new Point_FieldAccessUsingPrimary_2(7, 9);
        int viaRef = p.y;
        check(viaRef == 9);
    }

    static Point_FieldAccessUsingPrimary_2 make() {
        return new Point_FieldAccessUsingPrimary_2(1, 2);
    }
}

class Point_FieldAccessUsingPrimary_2 {
    int x;
    int y;

    Point_FieldAccessUsingPrimary_2(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
