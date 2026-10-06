package org.strata.jverify.langref.jls10.s10_1;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: an array type is written as the name of an element
// type followed by [] (one-dimensional), or repeated [] (multidimensional).
// Exercises primitive array types, reference array types, and multidimensional
// array types, including the component-type vs element-type distinction.
class ArrayTypes_8 {
    static void run() {

        // The alternative declarator style "int x[]" denotes the same array type.
        int legacy[] = {5, 6, 7};
        check(legacy.length == 3);
    }
}
