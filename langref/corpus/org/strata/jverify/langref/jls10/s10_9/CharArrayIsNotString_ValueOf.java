// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_9;

import static org.strata.jverify.JVerify.*;

// JLS 10.9 -- Array of char is not a String: use String.valueOf(char[]) to
// produce a String from a char[].
class CharArrayIsNotString_ValueOf {
    static void run() {
        char[] c = {'a', 'b'};
        check(String.valueOf(c).equals("ab"));
    }
}
