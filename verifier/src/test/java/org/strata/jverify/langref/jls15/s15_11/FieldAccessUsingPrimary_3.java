package org.strata.jverify.langref.jls15.s15_11;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.11.1 -- Field Access Using a Primary: accessing an instance field
// through a Primary expression (new Point_FieldAccessUsingPrimary_3().x) and through a local reference.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_11.FieldAccessUsingPrimary_3#run")
class FieldAccessUsingPrimary_3 {
    static void run() {

        // Field access where the Primary is a method invocation result.
        int viaMethod = make().x + make().y;
        check(viaMethod == 1 + 2);
    }

    static Point_FieldAccessUsingPrimary_3 make() {
        return new Point_FieldAccessUsingPrimary_3(1, 2);
    }
}

class Point_FieldAccessUsingPrimary_3 {
    int x;
    int y;

    Point_FieldAccessUsingPrimary_3(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
