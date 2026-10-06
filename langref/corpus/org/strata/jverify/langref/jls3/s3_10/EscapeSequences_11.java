package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.7 -- Escape Sequences: \b \s \t \n \f \r \" \' \\ and octal escapes
class EscapeSequences_11 {
    static void run() {

        String s = "\s"; // space escape (3.10.7)
        check(s.charAt(0) == ' ');
    }
}
