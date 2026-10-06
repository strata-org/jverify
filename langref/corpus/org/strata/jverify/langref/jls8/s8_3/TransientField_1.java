package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1.3 -- transient Fields: a transient field is not part of an object's persistent state;
// it behaves like an ordinary field at runtime (only affects default serialization).
class TransientField_1 {
    transient int cache;
    int value;

    static void run() {

        TransientField_1 t = new TransientField_1();
        t.value = 10;
        t.cache = t.value * 2;
        check(t.cache == 20);
    }
}
