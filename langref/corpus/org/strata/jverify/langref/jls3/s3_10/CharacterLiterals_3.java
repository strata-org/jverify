package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.4 -- Character Literals: single quote chars, escapes, unicode and octal escapes
class CharacterLiterals_3 {
    static void run() {

        char quote = '\'';
        check(quote == 39);
    }
}
