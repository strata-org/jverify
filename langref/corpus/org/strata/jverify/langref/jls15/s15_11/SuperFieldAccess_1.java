package org.strata.jverify.langref.jls15.s15_11;

import static org.strata.jverify.JVerify.*;

// JLS 15.11.2 -- Accessing Superclass Members using super: a subclass reads the
// hidden superclass field via super.field, distinguishing it from the field that
// shadows it in the subclass.
class SuperFieldAccess_1 {
    static void run() {

        Sub_SuperFieldAccess_1 s = new Sub_SuperFieldAccess_1();
        check(s.value == 20);
    }
}

class Base_SuperFieldAccess_1 {
    int value = 10;
}

class Sub_SuperFieldAccess_1 extends Base_SuperFieldAccess_1 {
    int value = 20;

    int readSuper() {
        return super.value;
    }

    int readOwn() {
        return this.value;
    }
}
