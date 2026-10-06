package org.strata.jverify.langref.jls10.s10_2;

import static org.strata.jverify.JVerify.*;

// JLS 10.2 -- Array Variables: a variable of array type holds a reference to an
// array; declaring the variable does not create an array; arrays are objects
// whose length is fixed at creation; multidimensional arrays are arrays of arrays.
class ArrayVariables_8 {
    static void run() {

        // The "C-style" array declarator after the variable name (legal but
        // discouraged) denotes the same array type.
        int d[] = new int[2];
        d[0] = 5;
        check(d.length == 2);
    }
}
