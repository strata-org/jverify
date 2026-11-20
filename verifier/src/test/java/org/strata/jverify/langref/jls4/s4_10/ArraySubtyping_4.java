package org.strata.jverify.langref.jls4.s4_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.3 -- Subtyping among Array Types: if S <: T (reference types) then
// S[] <: T[] (covariant arrays); also Object[] <: Object, Cloneable, Serializable
// and any reference array type is a subtype of Object.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_10.ArraySubtyping_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArraySubtyping_4 {
    static void run() {

        // int[] (primitive element) is a subtype of Object but not covariant.
        int[] ints = new int[] { 9 };
        Object o = ints;
        check(o == ints);
    }
}

class Base_ArraySubtyping_4 {
    int id() { return 1; }
}

class Sub_ArraySubtyping_4 extends Base_ArraySubtyping_4 {
    int id() { return 2; }
}
