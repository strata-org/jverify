package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: the primitive type keywords 'byte', 'short', 'float', 'double'.
class Keywords_15 {
    static void run() {

        byte b = 1;
        short s = 2;
        float f = 3.0f;
        double d = 4.0;
        check(b + s + f + d == 10.0);
    }
}
