package org.strata.jverify.langref.jls4.s4_12;

import static org.strata.jverify.JVerify.*;

// JLS 4.12.5 -- Initial Values of Variables: char[] components default to the null character (U+0000).
class InitialValues_ArrayChar {
    static void run() {

        char[] a = new char[2];
        check(a[0] == 0 && a[1] == 0);
    }
}
