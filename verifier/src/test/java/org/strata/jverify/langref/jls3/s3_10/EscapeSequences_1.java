package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.7 -- Escape Sequences: \b \s \t \n \f \r \" \' \\ and octal escapes
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.EscapeSequences_1#run")
class EscapeSequences_1 {
    static void run() {

        check('\b' == 8);
    }
}
