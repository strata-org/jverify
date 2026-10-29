// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.9 -- Array of char is not a String: use String.valueOf(char[]) to
// produce a String from a char[].
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_9.CharArrayIsNotString_ValueOf#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CharArrayIsNotString_ValueOf {
    static void run() {
        char[] c = {'a', 'b'};
        check(String.valueOf(c).equals("ab"));
    }
}
