package org.strata.jverify.langref.jls3.s3_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.11 -- Separators: ( ) { } [ ] ; , . ... @ ::
// Exercises each of the twelve separator tokens in a single running program.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_11.Separators_5#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Separators_5 {
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

        // '.' field access on an instance.
        Separators_5 obj = new Separators_5();
        check(obj.field == 7);
    }
}
