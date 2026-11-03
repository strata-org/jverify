package org.strata.jverify.langref.jls6.s6_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.4.1 -- Shadowing: a formal parameter of a method shadows a field
// with the same name for the entire body of the method.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_4.Shadowing_3#run")
class Shadowing_3 {
    static void run() {
        check(new Holder3().m(99) == 99);
    }
}

class Holder3 {
    int x = 1;

    int m(int x) { // parameter shadows the field
        return x;
    }
}
