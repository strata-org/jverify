package org.strata.jverify.langref.jls9.s9_4;

import static org.strata.jverify.JVerify.*;

// JLS 9.4 -- an interface may declare a generic (type-parameterised) method;
// the implementing method restates the type parameter list.
class Interface_GenericMethod {
    interface I {
        <T> T pick(T a, T b);
    }

    static class C implements I {
        public <T> T pick(T a, T b) {
            return a;
        }
    }

    static void run() {
        check(new C().pick("x", "y").equals("x"));
    }
}
