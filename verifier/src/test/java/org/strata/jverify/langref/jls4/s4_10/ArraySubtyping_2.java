package org.strata.jverify.langref.jls4.s4_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.3 -- Subtyping among Array Types: if S <: T (reference types) then
// S[] <: T[] (covariant arrays); also Object[] <: Object, Cloneable, Serializable
// and any reference array type is a subtype of Object.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_10.ArraySubtyping_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArraySubtyping_2 {
    static void run() {

        Sub_ArraySubtyping_2[] subs = new Sub_ArraySubtyping_2[] { new Sub_ArraySubtyping_2() };

        // Any reference array type is a subtype of Object.
        Object o = subs;
        check(o != null);
    }
}

class Base_ArraySubtyping_2 {
    int id() { return 1; }
}

class Sub_ArraySubtyping_2 extends Base_ArraySubtyping_2 {
    int id() { return 2; }
}
