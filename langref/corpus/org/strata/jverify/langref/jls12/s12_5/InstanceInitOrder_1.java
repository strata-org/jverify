package org.strata.jverify.langref.jls12.s12_5;

import static org.strata.jverify.JVerify.*;

// JLS 12.5 -- Creation of New Class Instances: instance field initializers
// and instance initializer blocks run before the constructor body, in
// textual order. Here `a = 1`, then the block sets `a = 11`, then the
// constructor sets `a = 111`.
class InstanceInitOrder_1 {
    static void run() {
        check(new C_IIO1().a == 111);
    }
}

class C_IIO1 {
    int a = 1;
    {
        a = a + 10;
    }

    C_IIO1() {
        a = a + 100;
    }
}
