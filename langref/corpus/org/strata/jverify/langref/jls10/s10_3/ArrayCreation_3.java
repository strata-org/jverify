package org.strata.jverify.langref.jls10.s10_3;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation: array creation expressions (new T[n]) covering
// single-dimension arrays of primitives, multi-dimensional arrays with all/some
// dimension expressions provided, and array creation with initializers.
class ArrayCreation_3 {
    static void run() {

        // Single-dimension array creation with a dimension expression.
        // Elements are default-initialized (0 for int).
        int[] a = new int[3];
        a[1] = 7;
        check(a[1] == 7);
    }
}
