package org.strata.jverify.langref.jls9.s9_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 9.8 -- java.util.function.Function<T,R> is a standard-library functional
// interface, usable as the target type of a lambda expression.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls9.s9_8.FunctionalInterface_Stdlib#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FunctionalInterface_Stdlib {
    static void run() {
        java.util.function.Function<Integer, Integer> f = x -> x * 2;
        check(f.apply(3) == 6);
    }
}
