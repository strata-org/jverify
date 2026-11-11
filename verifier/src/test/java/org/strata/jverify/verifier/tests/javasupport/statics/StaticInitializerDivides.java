package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A static initializer is one whenever it contains anything but javac's
 * {@code $assertionsDisabled} and {@code $VALUES} assignments, so a division
 * in it is checked like one in any method.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsInvalid = 1, errorCount = 1)
class StaticInitializerDivides {

    static class Holder {
        static int q;

        static {
            int z = 0;
            q = 1 / z;
//          ^^^^^^^^^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown does not hold
        }
    }

    static int use() {
        return Holder.q;
    }
}
