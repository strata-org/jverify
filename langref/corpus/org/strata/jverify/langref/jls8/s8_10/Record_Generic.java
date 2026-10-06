package org.strata.jverify.langref.jls8.s8_10;

import static org.strata.jverify.JVerify.*;

// JLS 8.10 -- a record may declare type parameters; the components can then be
// parameterised by those type variables.
class Record_Generic {
    record Pair<A, B>(A a, B b) {
    }

    static void run() {
        Pair<Integer, String> p = new Pair<>(1, "one");
        check(p.a() == 1 && p.b().equals("one"));
    }
}
