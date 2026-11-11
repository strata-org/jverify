package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A static initializer runs once, before any other code of its class, so its
 * class's static fields hold their default values when it starts: the
 * increment cannot overflow, and the assertion holds.
 */
@JVerifyTest(methodsVerified = 3) // the static initializer, read, and the implicit constructor
class StaticInitializerStartsFromDefaults {

    static int count;
    static int base;

    static {
        count++;
        base = base + 10;
        check(count == 1 && base == 10);
    }

    static int read() {
        return count;
    }
}
