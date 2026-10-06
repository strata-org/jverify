package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: 'class' and 'extends' declaring a subclass.
class Keywords_10 {
    static void run() {

        Sub_Keywords_10 s = new Sub_Keywords_10();
        check(s.v == 1 && s.w == 2);
    }
}

class Base_Keywords_10 {
    int v = 1;
}

class Sub_Keywords_10 extends Base_Keywords_10 {
    int w = 2;
}
