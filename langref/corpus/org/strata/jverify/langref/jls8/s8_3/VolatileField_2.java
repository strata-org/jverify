package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1.4 -- volatile Fields: a volatile field guarantees visibility of writes across threads;
// in a single thread it reads and writes like an ordinary field.
class VolatileField_2 {
    volatile boolean flag;
    volatile long count;

    static void run() {

        VolatileField_2 v = new VolatileField_2();
        v.count = 100L;
        v.count = v.count + 1L;
        check(v.count == 101L);
    }
}
