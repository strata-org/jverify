package org.strata.jverify.langref.jls15.s15_8;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.2 -- Class Literals: TypeName.class, primitive.class, and void.class
class ClassLiterals_4 {
    static void run() {

        Class<?> prim = int.class;
        Class<?> arr = int[].class;
        check(arr != prim);
    }
}
