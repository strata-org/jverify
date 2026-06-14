package org.strata.jverify.verifier.tests.javasupport.records;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A record/class type used ONLY in `new T(...)` position (never in a type
 * position such as a parameter, local, or return) must still have its opaque
 * composite sort declared, so the `new_(T)` value resolves.
 */
@JVerifyTest(methodsVerified = 3, errorCount = 0)
class NewOnlyRecordType {
    record Q(int x) {}

    static void make() {
        new Q(1);
    }
}
