package org.strata.jverify.langref.jls10.s10_2;

import static org.strata.jverify.JVerify.*;

// JLS 10.2 -- Array Variables: a variable of array type holds a reference to an
// array; declaring the variable does not create an array; arrays are objects
// whose length is fixed at creation; multidimensional arrays are arrays of arrays.
class ArrayVariables_14 {
    static void run() {

        // Array of a reference type; default element value is null.
        String[] s = new String[2];
        check(s.length == 2);
    }
}
