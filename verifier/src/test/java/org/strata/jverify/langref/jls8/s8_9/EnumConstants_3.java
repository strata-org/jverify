package org.strata.jverify.langref.jls8.s8_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.9.1 -- Enum Constants: declaring enum constants, some with constructor arguments.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_9.EnumConstants_3#run")
class EnumConstants_3 {
    enum Planet {
        MERCURY(1), VENUS(2), EARTH(3);

        final int order;

        Planet(int order) {
            this.order = order;
        }
    }

    static void run() {

        Planet p = Planet.EARTH;

        check(p == Planet.EARTH);
    }
}
