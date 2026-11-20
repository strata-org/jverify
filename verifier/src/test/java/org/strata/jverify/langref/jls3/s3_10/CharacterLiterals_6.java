package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.4 -- Character Literals: single quote chars, escapes, unicode and octal escapes
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.CharacterLiterals_6#run")
class CharacterLiterals_6 {
    static void run() {
 // 'A'
        char oct = '\101';
        check(oct == 'A');
    }
}
