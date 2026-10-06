package org.strata.jverify.langref.jls4.s4_5;

import static org.strata.jverify.JVerify.*;

// JLS 4.5.1 -- Diamond operator infers the type arguments of the constructor
// call from the target type of the enclosing expression.
class DiamondOperator_1 {
    static void run() {

        java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
        l.add(1);
        check(l.get(0) == 1);
    }
}
