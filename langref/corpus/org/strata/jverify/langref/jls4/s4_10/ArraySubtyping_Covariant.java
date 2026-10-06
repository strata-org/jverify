package org.strata.jverify.langref.jls4.s4_10;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.3 -- Subtyping among Array Types: Integer[] <: Number[]
// (reference array types are covariant).
class ArraySubtyping_Covariant {
    static void run() {

        Integer[] ints = { 1, 2, 3 };
        Number[] nums = ints;
        check(nums[0].intValue() == 1);
    }
}
