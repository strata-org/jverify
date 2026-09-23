package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An opted-out method still counts when deciding whether a name is overloaded.
 *
 * <p>A skipped {@code @Pure} method keeps an opaque DECLARATION, so it is emitted under
 * whatever name the overload suffix gives it. If the opted-out sibling were excluded from
 * the signature count, each of these would see a single signature, both would emit a bare
 * {@code dup}, and Strata would get a duplicate procedure plus an ambiguous call.
 *
 * <p>{@code fromInt} proves the verified overload still resolves to its own body;
 * {@code fromOptedOut} proves the opted-out one is callable but tells the caller nothing,
 * so the assertion on its result cannot be proved.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsVerified = 3, errorCount = 1)
class OptedOutOverloadSibling {

    @Pure
    static int dup(int x) {
        postcondition((int r) -> r == 7);
        return 7;
    }

    @Verify(false)
    @Pure
    static int dup(boolean b) {
        return 9;
    }

    static void fromInt() {
        check(dup(1) == 7);
    }

    static void fromOptedOut() {
        check(dup(true) == 9);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
