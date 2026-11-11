package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.2 -- Class Literals: TypeName.class, primitive.class, and void.class
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.ClassLiterals_5#run")
class ClassLiterals_5 {
    static void run() {

        Class<?> v = void.class;
        check(v == void.class);
    }
}
