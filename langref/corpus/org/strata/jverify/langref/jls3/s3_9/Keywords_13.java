package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: the 'enum' keyword declares an enum type.
class Keywords_13 {
    static void run() {

        check(E_Keywords_13.A.ordinal() == 0);
    }
}

enum E_Keywords_13 { A, B }
