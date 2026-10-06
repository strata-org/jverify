package org.strata.jverify.langref.jls8.s8_10;

import static org.strata.jverify.JVerify.*;

// JLS 8.10.2 -- record body may declare static members, including static
// factory methods that construct record instances.
class Record_StaticFactory {
    record P(int x) {
        public static P zero() {
            return new P(0);
        }
    }

    static void run() {
        check(P.zero().x() == 0);
    }
}
