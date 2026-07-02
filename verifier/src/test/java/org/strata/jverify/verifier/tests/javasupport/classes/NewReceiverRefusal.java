package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * A method call on a freshly-allocated receiver (`new T().m()`) is refused
 * gracefully: the `new T()` receiver lowers to an opaque `new_(T)` value that
 * has no shape to pass as the callee's `self` parameter, so emitting the call
 * would make Strata fail to unify the argument. Capturing constructor-allocated
 * values is Step 8a. The refusal is reported here because the enclosing method
 * is static.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 3, methodsSkipped = 1, errorCount = 0)
class NewReceiverRefusal {
    static class Box {
        @Pure
        int value() {
            return 0;
        }
    }

    static void callOnFreshReceiver() {
//              ^ error: method call on a freshly-allocated receiver is not yet supported
        var ignored = new Box().value();
    }
}
