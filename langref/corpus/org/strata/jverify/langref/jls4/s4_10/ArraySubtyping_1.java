package org.strata.jverify.langref.jls4.s4_10;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.3 -- Subtyping among Array Types: if S <: T (reference types) then
// S[] <: T[] (covariant arrays); also Object[] <: Object, Cloneable, Serializable
// and any reference array type is a subtype of Object.
class ArraySubtyping_1 {
    static void run() {

        Sub_ArraySubtyping_1[] subs = new Sub_ArraySubtyping_1[] { new Sub_ArraySubtyping_1() };
        // Sub_ArraySubtyping_1 <: Base_ArraySubtyping_1 implies Sub_ArraySubtyping_1[] <: Base_ArraySubtyping_1[] (covariant array subtyping).
        Base_ArraySubtyping_1[] bases = subs;
        check(bases[0].id() == 2);
    }
}

class Base_ArraySubtyping_1 {
    int id() { return 1; }
}

class Sub_ArraySubtyping_1 extends Base_ArraySubtyping_1 {
    int id() { return 2; }
}
