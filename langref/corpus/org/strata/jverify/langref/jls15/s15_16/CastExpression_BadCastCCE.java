package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: a reference cast that fails the run-time type
// check throws ClassCastException.
class CastExpression_BadCastCCE {
    static void run() {

        Object o = "hi";
        boolean caught = false;
        try {
            Integer i = (Integer) o;
        } catch (ClassCastException e) {
            caught = true;
        }
        check(caught);
    }
}
