package org.strata.jverify.langref.jls15.s15_12;

import static org.strata.jverify.JVerify.*;

// JLS 15.12 -- Method Invocation: a varargs method invoked with no arguments
// receives a zero-length array.
class VarargsInvocation_Zero {
    static int count(int... xs) { return xs.length; }

    static void run() {

        check(count() == 0);
    }
}
