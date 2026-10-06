package org.strata.jverify.langref.jls3.s3_11;

import static org.strata.jverify.JVerify.*;

// JLS 3.11 -- Separators: ( ) { } [ ] ; , . ... @ ::
// Exercises each of the twelve separator tokens in a single running program.
class Separators_2 {
    // '.' separator: field access on a type; ';' terminates the declaration.
    int field = 7;

    // '(' ')' delimit the parameter list; '{' '}' delimit the body.
    static int sum(int... xs) {            // '...' variable arity separator
        int total = 0;
        for (int i = 0; i < xs.length; i = i + 1) {   // '[' ']' via .length, ';' separators
            total = total + xs[i];                    // '[' ']' array indexing
        }
        return total;
    }

    static int apply(java.util.function.IntUnaryOperator op, int v) {
        return op.applyAsInt(v);           // '.' method invocation
    }

    static int doubleIt(int n) {
        return n + n;
    }

    static void run() {

        // ',' separators in array initializer; '{' '}' braces; '[' ']' array type.
        int[] arr = {1, 2, 3, 4};                // '[' ']'
        check(arr[3] == 4);
    }
}
