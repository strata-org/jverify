package org.strata.jverify.langref.jls15.s15_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.8.2 -- Class Literals: TypeName.class, primitive.class, and void.class
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_8.ClassLiterals_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ClassLiterals_1 {
    static void run() {

        Class<Integer> boxed = Integer.class;
        check(boxed == Integer.class);
    }
}
