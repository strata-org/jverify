package org.strata.jverify.langref.jls8.s8_7;

import static org.strata.jverify.JVerify.*;

// JLS 8.7 -- Static Initializers: a static initializer block declared in a class is executed
// when the class is initialized, in textual order with any static field initializers. Here a
// static field is set inside a static initializer block and observed from run().
class StaticInitializer_1 {
    static int computed;
    static int doubled;

    static {
        computed = 6 * 7;
    }

    static {
        doubled = computed * 2;
    }

    static void run() {

        check(computed == 42);
    }
}
