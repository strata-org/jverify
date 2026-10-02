package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * A void library method without a contract may write any heap location, so a
 * call to one must not let the caller carry a field fact across it. Locals are
 * untouched by the call, so a fact about one still holds afterwards.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 2, errorCount = 1)
class MissingContractVoidCallFrame {
    static class Box {
        int f;
    }

    static void m(Box b) {
        b.f = 0;
        int x = 1;
        System.gc();
//      ^ warning: missing contract for method 'gc' in class 'java.lang.System'
        check(x == 1);
        check(b.f == 0);
//      ^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
