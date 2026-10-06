package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.6 -- Narrowing Reference Conversion: supertype to subtype via cast, checked at runtime
class NarrowingReferenceConversion_2 {
    static void run() {

        NrcShape_NarrowingReferenceConversion_2 shape = new NrcCircle_NarrowingReferenceConversion_2(); // narrowing supertype -> subtype
        Object obj = shape;
        NrcShape_NarrowingReferenceConversion_2 back = (NrcShape_NarrowingReferenceConversion_2) obj;
        check(back.area() == 9);
    }
}

class NrcShape_NarrowingReferenceConversion_2 {
    int area() { return 0; }
}

class NrcCircle_NarrowingReferenceConversion_2 extends NrcShape_NarrowingReferenceConversion_2 {
    int area() { return 9; }
}
