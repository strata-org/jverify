package org.strata.jverify.langref.jls8.s8_1;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.1.4 -- static (nested) Classes: a static member class has no
// enclosing instance and is instantiated without an outer object.
class StaticNestedClass {
    static class Counter {
        private int value;

        int incrementAndGet() {
            value = value + 1;
            return value;
        }
    }

    static void run() {
        Counter c = new Counter();
        c.incrementAndGet();
        int v = c.incrementAndGet();
        check(v == 2);
    }
}
