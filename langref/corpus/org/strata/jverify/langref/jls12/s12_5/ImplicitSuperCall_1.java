package org.strata.jverify.langref.jls12.s12_5;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.7 -- If a constructor body does not begin with an explicit
// constructor invocation, an implicit `super();` is inserted. Here `SubISC1`
// has no explicit invocation, but `BaseISC1`'s no-arg constructor still runs
// and assigns `b = 7`.
class ImplicitSuperCall_1 {
    static void run() {
        check(new SubISC1().b == 7);
    }
}

class BaseISC1 {
    int b;

    BaseISC1() {
        b = 7;
    }
}

class SubISC1 extends BaseISC1 {
    int s;

    SubISC1() {
        s = 1;
    }
}
