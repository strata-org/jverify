package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- ArrayStoreException when storing an element into an array
// whose runtime component type is incompatible.
class ArrayStoreException_1 {
    static void run() {
        Object[] o = new String[1];
        boolean caught = false;
        try {
            o[0] = Integer.valueOf(42);
        } catch (ArrayStoreException e) {
            caught = true;
        }
        check(caught);
    }
}
