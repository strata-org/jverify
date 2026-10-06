package org.strata.jverify.langref.jls15.s15_8;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.2 -- Class Literals: TypeName.class, primitive.class, and void.class
class ClassLiterals_3 {
    static void run() {

        Class<Integer> boxed = Integer.class;
        Class<?> prim = int.class;
        check(prim != boxed);
    }
}
