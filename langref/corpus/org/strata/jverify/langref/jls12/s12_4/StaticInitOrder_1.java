package org.strata.jverify.langref.jls12.s12_4;

import static org.strata.jverify.JVerify.*;

// JLS 12.4.2 -- Detailed Initialization Procedure: static field initializers
// and static initializer blocks execute in textual order during class
// initialization. Here `a = 1`, then the block sets `a = 11`, then `b = a`.
class StaticInitOrder_1 {
    static void run() {
        check(C_SIO1.b == 11);
    }
}

class C_SIO1 {
    static int a = 1;
    static {
        a = a + 10;
    }
    static int b = a;
}
