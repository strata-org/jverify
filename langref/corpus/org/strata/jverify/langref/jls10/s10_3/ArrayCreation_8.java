package org.strata.jverify.langref.jls10.s10_3;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation: array creation expressions (new T[n]) covering
// single-dimension arrays of primitives, multi-dimensional arrays with all/some
// dimension expressions provided, and array creation with initializers.
class ArrayCreation_8 {
    static void run() {

        // Multi-dimensional array creation with all dimensions provided.
        int[][] grid = new int[2][3];
        check(grid[0].length == 3 && grid[1].length == 3);
    }
}
