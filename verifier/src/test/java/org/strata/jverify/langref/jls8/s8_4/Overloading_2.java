package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.9 -- Overloading: several methods share a name but differ in the
// number or types of their formal parameters; the most specific applicable
// method is chosen at compile time from the argument types.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.Overloading_2#run")
class Overloading_2 {
    // Overload by parameter count.
    static int f(int a) {
        return a + 1;
    }

    static int f(int a, int b) {
        return a + b;
    }

    // Overload by parameter type (long is selected for long arguments).
    static int f(long a) {
        return (int) (a + 100);
    }

    static void run() {

        check(f(2, 3) == 5);
    }
}
