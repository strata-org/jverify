package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A class with a static initializer, used from another class. The first use
 * may run the initializer, which may write other static fields, so a value
 * read before the use is not known to be unchanged after it.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, errorCount = 2)
class StaticInitializerGuard {

    static int observed;

    static class Holder {
        static int value = 5;

        static {
            observed = 7;
        }

        static int get() {
            return value;
        }
    }

    static void initializerMayRun() {
        observed = 1;
        int v = Holder.value;
        check(observed == 1);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold; a static field may have been changed by the static initializer of 'Holder', which this method may run
    }

    static void valueIsNotKnown() {
        int v = Holder.value;
        check(v == 5);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void ownFieldIsKept() {
        observed = 1;
        int v = Holder.value;
        int w = Holder.value;
        check(v == w);
    }
}
