package org.strata.jverify.langref.jls10.s10_3;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation: array creation expressions (new T[n]) covering
// single-dimension arrays of primitives, multi-dimensional arrays with all/some
// dimension expressions provided, and array creation with initializers.
class ArrayCreation_16 {
    static void run() {

        // Array of a primitive type other than int.
        boolean[] flags = new boolean[2];
        check(flags[0] == false && flags[1] == false);
    }
}
