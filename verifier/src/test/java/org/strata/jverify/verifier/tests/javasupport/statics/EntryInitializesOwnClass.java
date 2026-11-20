package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The JVM initializes a class before it runs the class's own entry method, so
 * the entry method starts by initializing its class. The assertion that the
 * field still has its default value fails, and the message names the
 * initializer. (A boolean field: an entry method cannot use an {@code int}
 * static field on the Laurel pipeline.)
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, errorCount = 1)
class EntryInitializesOwnClass {

    static boolean ready = true;

    @InterpretEntry
    static void main() {
        check(!ready);
//      ^^^^^^^^^^^^^ Error: assertion does not hold; a static field may have been changed by the static initializer of 'EntryInitializesOwnClass', which this method may run
    }
}
