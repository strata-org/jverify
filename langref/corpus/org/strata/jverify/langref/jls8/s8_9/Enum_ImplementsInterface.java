package org.strata.jverify.langref.jls8.s8_9;

import static org.strata.jverify.JVerify.*;

// JLS 8.9 -- an enum type may implement one or more interfaces; each constant
// then satisfies the interface contract.
class Enum_ImplementsInterface {
    interface Named {
        String label();
    }

    enum E implements Named {
        A, B;

        public String label() {
            return name().toLowerCase();
        }
    }

    static void run() {
        check(E.A.label().equals("a"));
    }
}
