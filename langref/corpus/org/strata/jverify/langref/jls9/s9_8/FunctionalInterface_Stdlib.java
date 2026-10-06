package org.strata.jverify.langref.jls9.s9_8;

import static org.strata.jverify.JVerify.*;

// JLS 9.8 -- java.util.function.Function<T,R> is a standard-library functional
// interface, usable as the target type of a lambda expression.
class FunctionalInterface_Stdlib {
    static void run() {
        java.util.function.Function<Integer, Integer> f = x -> x * 2;
        check(f.apply(3) == 6);
    }
}
