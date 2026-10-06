package org.strata.jverify.langref.jls10.s10_1;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: an array type is written as the name of an element
// type followed by [] (one-dimensional), or repeated [] (multidimensional).
// Exercises primitive array types, reference array types, and multidimensional
// array types, including the component-type vs element-type distinction.
class ArrayTypes_2 {
    static void run() {

        // One-dimensional array type of a primitive element type (int[]).
        int[] ints = new int[3];
        ints[0] = 10;
        ints[1] = 20;
        ints[2] = 30;
        check(ints[0] + ints[1] + ints[2] == 60);
    }
}
