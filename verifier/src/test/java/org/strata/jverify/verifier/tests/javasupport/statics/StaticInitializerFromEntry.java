package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An entry method starts with no class initialized, so its first use of
 * {@code Config} runs the static initializer, and the value read is the one the
 * initializer assigned.
 *
 * <p>Proving that needs the caller to see what the initializer does. The
 * Cortex pipeline is given the initializer to inline; on the Laurel pipeline
 * the initializer is an opaque procedure without a postcondition, so the
 * assertion is not proved there.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER})
class StaticInitializerFromEntry {

    static class Config {
        static boolean enabled = true;
    }

    @InterpretEntry
    static void main() {
        check(Config.enabled);
    }
}
