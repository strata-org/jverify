package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.3.3 -- final Methods: a final instance method cannot be overridden by
// subclasses; the subclass inherits and invokes the final implementation.
class FinalMethods_1 {
    static void run() {

        Base_FinalMethods_1 b = new Derived_FinalMethods_1();
        check(b.identity(9) == 9);
    }
}

class Base_FinalMethods_1 {
    // A final method may not be overridden in any subclass.
    final int identity(int n) {
        return n;
    }

    int tag() {
        return 1;
    }
}

class Derived_FinalMethods_1 extends Base_FinalMethods_1 {
    // tag() is not final, so it may be overridden; identity() is inherited as-is.
    @Override
    int tag() {
        return 7;
    }
}
