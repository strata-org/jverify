package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.3.2 -- static Methods: a class (static) method is not associated with
// an instance and is invoked on the class itself; it may call other static methods.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.StaticMethods_1#run")
class StaticMethods_1 {
    static int doubled(int n) {
        return n * 2;
    }

    static int quadrupled(int n) {
        // A static method may invoke another static method without an instance.
        return doubled(doubled(n));
    }

    static void run() {

        check(doubled(5) == 10);
    }
}
