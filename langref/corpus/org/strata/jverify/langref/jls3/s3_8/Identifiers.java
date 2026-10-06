package org.strata.jverify.langref.jls3.s3_8;

import static org.strata.jverify.JVerify.*;

// JLS 3.8 -- Identifiers: identifier composition (Java letters incl. _ and $, Java letter-or-digit),
// contextual keywords used as identifiers, and Unicode letters as identifier characters.
class Identifiers {
    static void run() {
        // An identifier may start with a letter, underscore, or dollar sign.
        int simple = 1;
        int _underscore = 2;
        int $dollar = 3;
        // Identifiers may contain digits after the first character.
        int x1 = 4;
        int a2b3 = 5;
        // Mixed letters, digits, underscores, and dollar signs.
        int mixed_Name$2 = 6;

        // Contextual keywords (e.g. var, record, yield, sealed, permits) are not
        // reserved and remain valid identifiers in this context.
        int var = 7;
        int record = 8;
        int yield = 9;
        int sealed = 10;
        int permits = 11;

        // Unicode letters are Java letters and may appear in identifiers.
        int α = 12; // Greek small letter alpha
        int café = 13; // 'e' with acute accent

        int total = simple + _underscore + $dollar + x1 + a2b3 + mixed_Name$2
                + var + record + yield + sealed + permits + α + café;
        check(total == 91);
    }
}
