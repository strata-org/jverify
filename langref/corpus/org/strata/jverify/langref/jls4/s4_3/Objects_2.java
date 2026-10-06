package org.strata.jverify.langref.jls4.s4_3;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.1 -- Objects: a class instance created with new, fields accessed via reference.
class Objects_2 {
    int value;

    Objects_2(int v) {
        this.value = v;
    }

    static void run() {

        Objects_2 o = new Objects_2(7);
        Objects_2 alias = o;
        check(o == alias);
    }
}
