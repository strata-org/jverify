package org.strata.jverify.langref.jls10.s10_3;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation: array creation expressions (new T[n]) covering
// single-dimension arrays of primitives, multi-dimensional arrays with all/some
// dimension expressions provided, and array creation with initializers.
class ArrayCreation_6 {
    static void run() {

        // Array creation with an array initializer (no dimension expression).
        int[] init = new int[] {10, 20, 30};
        check(init[0] == 10 && init[1] == 20 && init[2] == 30);
    }
}
