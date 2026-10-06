package org.strata.jverify.langref.jls10.s10_9;

import static org.strata.jverify.JVerify.*;

// JLS 10.9 -- An Array of Characters Is Not a String: a char[] holds chars
// individually; it is not a String, but each element can be read and compared.
class CharArrayIsNotString_2 {
    static void run() {

        char[] c = { 'h', 'e', 'l', 'l', 'o' };
        check(c[0] == 'h');
    }
}
