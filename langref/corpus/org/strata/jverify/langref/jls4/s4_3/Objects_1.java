package org.strata.jverify.langref.jls4.s4_3;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.1 -- Objects: a class instance created with new, fields accessed via reference.
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
