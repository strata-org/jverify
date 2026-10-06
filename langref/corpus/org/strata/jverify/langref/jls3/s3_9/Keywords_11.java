package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: 'this' and 'super' distinguish instance and superclass field access.
class Keywords_11 {
    static void run() {

        Sub_Keywords_11 s = new Sub_Keywords_11();
        check(s.diff() == 1);
    }
}

class Base_Keywords_11 {
    int x = 1;
}

class Sub_Keywords_11 extends Base_Keywords_11 {
    int x = 2;

    int diff() {
        return this.x - super.x;
    }
}
