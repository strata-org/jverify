package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.1 -- lower-bounded wildcard formal parameter type: a method taking
// List<? super Integer> can accept a List<Number> and write Integer values into it.
class WildcardParamSuper_1 {
    static void addOne(java.util.List<? super Integer> l) {
        l.add(1);
    }

    static void run() {
        java.util.List<Number> nums = new java.util.ArrayList<>();
        addOne(nums);
        check(nums.get(0).intValue() == 1);
    }
}
