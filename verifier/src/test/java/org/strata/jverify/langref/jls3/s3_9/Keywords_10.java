package org.strata.jverify.langref.jls3.s3_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: 'class' and 'extends' declaring a subclass.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_9.Keywords_10#run")
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
