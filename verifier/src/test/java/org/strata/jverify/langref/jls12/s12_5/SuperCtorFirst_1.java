package org.strata.jverify.langref.jls12.s12_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 12.5 -- Creation of New Class Instances: an explicit `super(...)` call
// (or an implicit one) causes the superclass constructor body to execute
// before the subclass constructor body, giving the observed order "BS".
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls12.s12_5.SuperCtorFirst_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SuperCtorFirst_1 {
    static StringBuilder log = new StringBuilder();

    static void run() {
        new SubSCF1();
        check(log.toString().equals("BS"));
    }
}

class BaseSCF1 {
    BaseSCF1() {
        SuperCtorFirst_1.log.append("B");
    }
}

class SubSCF1 extends BaseSCF1 {
    SubSCF1() {
        super();
        SuperCtorFirst_1.log.append("S");
    }
}
