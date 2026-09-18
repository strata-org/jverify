package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An instance method's contract is actually CHECKED, not assumed.
 *
 * <p>Before instance methods were translated, {@code visitMethodDef} matched neither
 * its static nor its {@code @InterpretEntry} branch, so a plain instance method was
 * neither translated nor demoted. {@code VerifyAnnotationCompiler} records status from
 * the annotation's intent at registration, before translation; {@code markSkipped} only
 * demotes afterward. The method stayed {@code Verified}, so a false contract passed
 * with exit code 0 -- measured pre-change on both methods below: "Found 0 errors,
 * Verified methods: 2, exit 0".
 *
 * <p>This fixture pins the fix, and would also catch a regression that stopped
 * translating instance methods: silence here means the obligations went unchecked,
 * not that they held.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 1, methodsInvalid = 2, errorCount = 2)
class InstanceContractsChecked {
    /** Postcondition contradicts the body. */
    int wrongPostcondition(int x) {
        postcondition((int r) -> r == x + 1000);
//                               ^^^^^^^^^^^^^ Error: postcondition does not hold
        return x;
    }

    /** Assertion that is false outright. */
    void falseAssertion() {
        check(1 == 2);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
