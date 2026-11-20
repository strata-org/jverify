// A user contract replaces the shipped contract of the wrapper class it
// describes, however its @Contract annotation is written, so the contract set
// loads. A @Contract in a comment replaces nothing, and neither does a user
// contract for another class: Byte keeps its shipped contract.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(contractSourcePath = {"usercontracts/wrapperreplacement"}, methodsVerified = 5, methodsSkipped = 0)
class UserContractReplacesWrapper {
    static void commentedOutContractReplacesNothing() {
        Integer i = 5;
        check(i.equals(5));
    }

    static void qualifiedAnnotationReplaces() {
        check(Long.max(1L, 2L) == 1L);
    }

    static void reorderedAnnotationReplaces() {
        check(Short.toUnsignedInt((short) 1) == 7);
    }

    static void otherContractReplacesNothing() {
        Byte b = (byte) 2;
        check(Math.negateExact(1) == 7 && b.equals((byte) 2));
    }
}
