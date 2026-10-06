package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.6 -- Narrowing Reference Conversion: supertype to subtype via cast, checked at runtime
class NarrowingReferenceConversion_1 {
    static void run() {

        NrcShape_NarrowingReferenceConversion_1 shape = new NrcCircle_NarrowingReferenceConversion_1();   // widening
        NrcCircle_NarrowingReferenceConversion_1 circle = (NrcCircle_NarrowingReferenceConversion_1) shape;      // narrowing Object -> NrcShape_NarrowingReferenceConversion_1
        check(circle.area() == 9);
    }
}

class NrcShape_NarrowingReferenceConversion_1 {
    int area() { return 0; }
}

class NrcCircle_NarrowingReferenceConversion_1 extends NrcShape_NarrowingReferenceConversion_1 {
    int area() { return 9; }
}
