package org.strata.jverify.langref.jls4.s4_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.3 -- Subtyping among Array Types: Integer[] <: Number[]
// (reference array types are covariant).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_10.ArraySubtyping_Covariant#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArraySubtyping_Covariant {
    static void run() {

        Integer[] ints = { 1, 2, 3 };
        Number[] nums = ints;
        check(nums[0].intValue() == 1);
    }
}
