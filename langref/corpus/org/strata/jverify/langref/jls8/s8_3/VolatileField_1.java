package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1.4 -- volatile Fields: a volatile field guarantees visibility of writes across threads;
// in a single thread it reads and writes like an ordinary field.
class VolatileField_1 {
    volatile boolean flag;
    volatile long count;

    static void run() {

        VolatileField_1 v = new VolatileField_1();
        v.flag = true;
        check(v.flag);
    }
}
