package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.5 -- Method Result: a method either declares a result type (and must
// return a value of that type) or is declared void (and returns no value).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.MethodResult_3#run")
class MethodResult_3 {
    // A method with a primitive result type must return an int value.
    static int answer() {
        return 42;
    }

    // A method with a reference result type returns a reference.
    static int[] pair(int a, int b) {
        return new int[] { a, b };
    }

    // A void method has no result and uses a return statement with no value.
    static int sink = 0;
    static void noResult() {
        sink = 42;
        return;
    }

    static void run() {

        noResult();
        check(sink == 42);
    }
}
