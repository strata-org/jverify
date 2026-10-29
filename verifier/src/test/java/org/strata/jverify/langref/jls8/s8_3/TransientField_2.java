package org.strata.jverify.langref.jls8.s8_3;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1.3 -- transient Fields: a transient field is not part of an object's persistent state;
// it behaves like an ordinary field at runtime (only affects default serialization).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_3.TransientField_2#run")
class TransientField_2 {
    transient int cache;
    int value;

    static void run() {

        TransientField_2 t = new TransientField_2();
        t.value = 10;
        check(t.value == 10);
    }
}
