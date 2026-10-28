// A contract stub whose only frame is a throwsOn case's modifies still writes the
// heap: a caller that catches the exception gets the inferred wildcard frame, so
// it is not reported for writing outside its frame, and its own caller cannot
// assume the written field is unchanged.
package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testing.ThrowingWriter;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(contractSourcePath = {"usercontracts/throwsonframe"}, exitCode = 4, methodsVerified = 3, methodsInvalid = 1, errorCount = 1)
class UserContractThrowsOnFrame {
    static class Box {
        int f;
    }

    static void catchesTheThrow(Box b) {
        try {
            ThrowingWriter.writeThenThrow(b);
        } catch (Exception e) {
        }
    }

    static void cannotAssumeTheFieldUnchanged(Box b) {
        precondition(b.f == 5);
        catchesTheThrow(b);
        check(b.f == 5);
//      ^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
