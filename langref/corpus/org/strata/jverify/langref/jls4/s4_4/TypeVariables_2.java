package org.strata.jverify.langref.jls4.s4_4;

import static org.strata.jverify.JVerify.*;

// JLS 4.4 -- Type Variables: a type variable is an unqualified identifier used as a
// type, introduced by a generic class/interface/method declaration. This exercises a
// generic class with a type variable T and a generic method with its own type variable.
class TypeVariables_2 {
    // Generic class introducing type variable T.
    static class Box<T> {
        T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    // Generic method introducing its own type variable U; T is used as a type.
    static <U> U identity(U input) {
        return input;
    }

    static void run() {

        Integer same = identity(7);
        check(same == 7);
    }
}
