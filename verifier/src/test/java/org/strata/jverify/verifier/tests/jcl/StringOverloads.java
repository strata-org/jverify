// object-contract.java's two substring overloads each bind to their own
// contract. The one-argument form bounds only its start; the two-argument form
// also orders its bounds, so (2, 1) fails where substring(2) holds.
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, methodsVerified = 3, methodsInvalid = 2, errorCount = 2)
class StringOverloads {
    static String dropTwo(String s) {
        if (s.length() >= 2) {
            return s.substring(2);
        }
        return s;
    }

    static String slice(String s) {
        if (s.length() >= 3) {
            return s.substring(1, 3);
        }
        return s;
    }

    static String reversedBounds(String s) {
        if (s.length() >= 2) {
            return s.substring(2, 1);
//                 ^^^^^^^^^^^^^^^^^ Error: precondition does not hold
        }
        return s;
    }

    static String unguardedDrop(String s) {
        return s.substring(1);
//             ^^^^^^^^^^^^^^ Error: precondition does not hold
    }
}
