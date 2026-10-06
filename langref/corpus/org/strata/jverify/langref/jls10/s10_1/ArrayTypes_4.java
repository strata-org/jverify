package org.strata.jverify.langref.jls10.s10_1;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: an array type is written as the name of an element
// type followed by [] (one-dimensional), or repeated [] (multidimensional).
// Exercises primitive array types, reference array types, and multidimensional
// array types, including the component-type vs element-type distinction.
class ArrayTypes_4 {
    static void run() {

        // One-dimensional array type of a reference element type (String[]).
        String[] words = new String[2];
        words[0] = "a";
        words[1] = "bc";
        check(words[0].length() + words[1].length() == 3);
    }
}
