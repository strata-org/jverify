// object-contract.java's String contract: length and isEmpty apply in bodies
// and specs. charAt returns a char from its body, a range Strata cannot check
// there, so charAt is refused and each method that calls it is refused alone,
// whether the call is guarded, follows a precondition, or is unguarded.
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsInvalid = 1, errorCount = 1, methodsVerified = 7, methodsSkipped = 4)
class StringContracts {
    static boolean firstCharStable(String s) {
//                                           ^ error: call to 'charAt', which could not be translated: a contract body that returns 'char' is not yet supported: Strata cannot check the result's range
        if (s.length() > 0) return s.charAt(0) == s.charAt(0);
        return false;
    }

    static char firstGuarded(String s) {
//                                     ^ error: call to 'charAt', which could not be translated: a contract body that returns 'char' is not yet supported: Strata cannot check the result's range
        if (s.length() > 0) {
            return s.charAt(0);
        }
        return ' ';
    }

    static char firstAfterPrecondition(String s) {
//                                               ^ error: call to 'charAt', which could not be translated: a contract body that returns 'char' is not yet supported: Strata cannot check the result's range
        precondition(s.length() > 0);
        return s.charAt(0);
    }

    static boolean nonEmptyHasLength(String s) {
        precondition(!s.isEmpty());
        return s.length() != 0;
    }

    int lengthOf(String s) {
        return s.length();
    }

    // length has a transparent body, so two calls on the same string agree.
    static int lengthAgrees(String s) {
        postcondition((int r) -> r == s.length());
        return s.length();
    }

    static int lengthOffByOne(String s) {
        postcondition((int r) -> r == s.length());
//                               ^^^^^^^^^^^^^^^ Error: postcondition does not hold
        return s.length() - 1;
    }

    // The other members are bounded by length(), so they agree with it.
    static void isEmptyAgreesWithLength(String s) {
        check(s.isEmpty() == (s.length() == 0));
    }

    static void indexOfBelowLength(String s) {
        int i = s.indexOf('a');
        check(-1 <= i && i < s.length());
    }

    static void substringAtLength(String s) {
        String t = s.substring(s.length());
    }

    static char firstUnguarded(String s) {
//                                       ^ error: call to 'charAt', which could not be translated: a contract body that returns 'char' is not yet supported: Strata cannot check the result's range
        return s.charAt(0);
    }
}
