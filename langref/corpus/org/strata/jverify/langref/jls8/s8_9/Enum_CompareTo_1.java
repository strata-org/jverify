package org.strata.jverify.langref.jls8.s8_9;

import static org.strata.jverify.JVerify.*;

// JLS 8.9.3 -- Enum implements Comparable<E>: compareTo orders constants by
// their declaration position (ordinal).
class Enum_CompareTo_1 {
    enum E {
        A, B, C
    }

    static void run() {
        check(E.A.compareTo(E.C) < 0);
    }
}
