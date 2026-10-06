package org.strata.jverify.langref.jls12.s12_4;

import static org.strata.jverify.JVerify.*;

// JLS 12.4.1 -- Before a class is initialized, its direct superclass must be
// initialized. Triggering initialization of `SubSIB1` therefore first
// initializes `BaseSIB1`, and the observed order is "B" then "S".
class SuperInitBeforeSub_1 {
    static StringBuilder log = new StringBuilder();

    static void run() {
        // Read a static field on the subclass to trigger initialization.
        int trigger = SubSIB1.marker;
        // Prevent constant-folding elimination and use `trigger` meaningfully.
        check(log.toString().equals("BS") && trigger == 1);
    }
}

class BaseSIB1 {
    static {
        SuperInitBeforeSub_1.log.append("B");
    }
}

class SubSIB1 extends BaseSIB1 {
    static int marker = 1;
    static {
        SuperInitBeforeSub_1.log.append("S");
    }
}
