package org.strata.jverify.langref.jls15.s15_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.9.1 -- Determining the Class being Instantiated: a plain class
// instance creation expression names the class to instantiate directly.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_9.DeterminingClassInstantiated_3#run")
class DeterminingClassInstantiated_3 {
    static void run() {

        Point_DeterminingClassInstantiated_3 p = new Point_DeterminingClassInstantiated_3(2, 3);
        check(p.sum() == 5);
    }
}

class Point_DeterminingClassInstantiated_3 {
    int x;
    int y;

    Point_DeterminingClassInstantiated_3(int x, int y) {
        this.x = x;
        this.y = y;
    }

    int sum() {
        return x + y;
    }
}
