package org.strata.jverify.langref.jls12.s12_5;

import static org.strata.jverify.JVerify.*;

// JLS 12.5 -- Creation of New Class Instances: an explicit `super(...)` call
// (or an implicit one) causes the superclass constructor body to execute
// before the subclass constructor body, giving the observed order "BS".
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
