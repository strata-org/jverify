package org.strata.jverify.langref.jls4.s4_10;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.3 -- Subtyping among Array Types: if S <: T (reference types) then
// S[] <: T[] (covariant arrays); also Object[] <: Object, Cloneable, Serializable
// and any reference array type is a subtype of Object.
class ArraySubtyping_3 {
    static void run() {

        // int[] (primitive element) is a subtype of Object but not covariant.
        int[] ints = new int[] { 9 };
        Object oi = ints;
        check(oi != null);
    }
}

class Base_ArraySubtyping_3 {
    int id() { return 1; }
}

class Sub_ArraySubtyping_3 extends Base_ArraySubtyping_3 {
    int id() { return 2; }
}
