package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.4 -- Generic Methods: a method declares its own type parameters,
// including a bounded type parameter, with the actual type arguments inferred.
class GenericMethods_2 {
    // A generic method with type parameter T, returning its argument unchanged.
    static <T> T identity(T value) {
        return value;
    }

    // A generic method with a bounded type parameter T extends Comparable<T>.
    static <T extends Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    static void run() {

        check(max(3, 8) == 8);
    }
}
