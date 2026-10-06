package org.strata.jverify.langref.jls10.s10_1;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: an array type is written as the name of an element
// type followed by [] (one-dimensional), or repeated [] (multidimensional).
// Exercises primitive array types, reference array types, and multidimensional
// array types, including the component-type vs element-type distinction.
class ArrayTypes_7 {
    static void run() {

        // Multidimensional array type (int[][]). Its component type is int[],
        // and its element type is int.
        int[][] grid = new int[2][2];
        grid[0][0] = 1;
        grid[0][1] = 2;
        grid[1][0] = 3;
        grid[1][1] = 4;
        check(grid[0][0] + grid[0][1] + grid[1][0] + grid[1][1] == 10);
    }
}
