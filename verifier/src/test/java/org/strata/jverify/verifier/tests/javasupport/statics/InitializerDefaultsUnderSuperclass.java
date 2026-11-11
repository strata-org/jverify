package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A class is marked as being initialized before its superclass is initialized
 * (JLS 12.4.2), so the superclass's initializer can assign the subclass's
 * static fields without starting its initialization again. The subclass's
 * initializer therefore cannot assume its fields at their defaults when a
 * type initialized before it has an initializer: here {@code x} is 5.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsInvalid = 1, errorCount = 1)
class InitializerDefaultsUnderSuperclass {

    static class Base {
        static {
            Sub.x = 5;
        }
    }

    static class Sub extends Base {
        static int x;

        static {
            check(x == 0);
//          ^^^^^^^^^^^^^^ Error: assertion does not hold
        }
    }

    static int use() {
        return Sub.x;
    }
}
