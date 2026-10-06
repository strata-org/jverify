package org.strata.jverify.langref.jls8.s8_10;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.3 -- a record provides an automatic hashCode() derived from its
// components; equal records must have equal hash codes.
class Record_HashCode {
    record P(int x) {
    }

    static void run() {
        P a = new P(5);
        P b = new P(5);
        check(a.hashCode() == b.hashCode());
    }
}
