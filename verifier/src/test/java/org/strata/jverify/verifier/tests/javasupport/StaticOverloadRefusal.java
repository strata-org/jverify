package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Overloads of a name are disambiguated by appending their erased parameter
 * types to the emitted Laurel procedure name, so {@code dup(int)} and
 * {@code dup(boolean)} become distinct procedures and both verify. A
 * non-overloaded name like {@code unique} keeps its bare name.
 * Verified 4 = both {@code dup} bodies + {@code unique} + the synthetic default
 * constructor.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 4,
        errorCount = 0
)
class StaticOverloadRefusal {

    @Pure
    static int dup(int x) {
        return x;
    }

    @Pure
    static int dup(boolean b) {
        return b ? 1 : 0;
    }

    @Pure
    static int unique(int x) {
        postcondition((int r) -> r == x);
        return x;
    }
}
