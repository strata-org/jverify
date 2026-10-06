package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.3.6 -- synchronized method: a synchronized static method acquires the
// class object's monitor for the duration of the call; result is unchanged.
class SynchronizedMethod_1 {
    static synchronized int add(int a, int b) {
        return a + b;
    }

    static void run() {
        check(add(2, 3) == 5);
    }
}
