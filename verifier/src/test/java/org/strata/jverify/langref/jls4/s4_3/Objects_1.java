package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.1 -- Objects: a class instance created with new, fields accessed via reference.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.Objects_1#run")
class Objects_1 {
    int value;

    Objects_1(int v) {
        this.value = v;
    }

    static void run() {

        Objects_1 o = new Objects_1(7);
        Objects_1 alias = o;
        alias.value = 42;
        // Both references denote the same object instance.
        check(o.value == 42);
    }
}
