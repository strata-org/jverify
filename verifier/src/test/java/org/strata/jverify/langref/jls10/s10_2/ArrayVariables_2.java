package org.strata.jverify.langref.jls10.s10_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.2 -- Array Variables: a variable of array type holds a reference to an
// array; declaring the variable does not create an array; arrays are objects
// whose length is fixed at creation; multidimensional arrays are arrays of arrays.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_2.ArrayVariables_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayVariables_2 {
    static void run() {

        // A variable of array type holds a reference to an array object.
        int[] a = new int[3];
        a[0] = 10;
        a[1] = 20;
        a[2] = 30;
        check(a[0] == 10);
    }
}
