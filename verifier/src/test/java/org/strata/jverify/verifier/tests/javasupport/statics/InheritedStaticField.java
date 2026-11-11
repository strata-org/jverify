package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code Sub.x} names a field declared in {@code Base}. The JVM initializes
 * the declaring class, not the class named in the access: the message names
 * {@code Base}, and {@code Sub}'s initializer is not run.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, errorCount = 1)
class InheritedStaticField {

    static int log;

    static class Base {
        static int x = 1;

        static {
            log = 1;
        }
    }

    static class Sub extends Base {
        static {
            log = 2;
        }
    }

    static void readThroughSubclass() {
        log = 0;
        int before = log;
        int v = Sub.x;
        check(log == before);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold; a static field may have been changed by the static initializer of 'Base', which this method may run
    }
}
