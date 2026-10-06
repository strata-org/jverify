package org.strata.jverify.langref.jls9.s9_4;

import static org.strata.jverify.JVerify.*;

// JLS 9.4.3 -- a default interface method may invoke another default method of
// the same interface; both are inherited by an implementing class.
class Default_CallsDefault {
    interface I {
        default int a() {
            return 1;
        }

        default int b() {
            return a() + 10;
        }
    }

    static class C implements I {
    }

    static void run() {
        check(new C().b() == 11);
    }
}
