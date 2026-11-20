package org.strata.jverify.langref.jls10.s10_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.9 -- An Array of Characters Is Not a String: a char[] holds chars
// individually; it is not a String, but each element can be read and compared.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_9.CharArrayIsNotString_5#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class CharArrayIsNotString_5 {
    static void run() {

        char[] c = { 'h', 'e', 'l', 'l', 'o' };
        // Mutate an element to confirm it is an array, not an immutable String.
        c[0] = 'H';
        check(c[0] == 'H');
    }
}
