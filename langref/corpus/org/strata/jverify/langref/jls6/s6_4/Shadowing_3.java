package org.strata.jverify.langref.jls6.s6_4;

import static org.strata.jverify.JVerify.*;

// JLS 6.4.1 -- Shadowing: a formal parameter of a method shadows a field
// with the same name for the entire body of the method.
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
