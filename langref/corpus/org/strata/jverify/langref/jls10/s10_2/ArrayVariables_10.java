package org.strata.jverify.langref.jls10.s10_2;

import static org.strata.jverify.JVerify.*;

// JLS 10.2 -- Array Variables: a variable of array type holds a reference to an
// array; declaring the variable does not create an array; arrays are objects
// whose length is fixed at creation; multidimensional arrays are arrays of arrays.
class ArrayVariables_10 {
    static void run() {

        // Multidimensional arrays are arrays whose components are arrays.
        int[][] m = new int[2][3];
        m[0][0] = 1;
        m[1][2] = 7;
        check(m.length == 2);
    }
}
