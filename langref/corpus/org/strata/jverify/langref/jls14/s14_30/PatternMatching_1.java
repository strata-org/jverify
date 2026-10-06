package org.strata.jverify.langref.jls14.s14_30;

import static org.strata.jverify.JVerify.*;

// JLS 14.30.2 -- Pattern Matching: a value matches a type pattern iff it is non-null and an instance of the type.
class PatternMatching_1 {
    static void run() {

        Object matching = "hello";

        // A non-null instance matches the type pattern and the binding is in scope.
        boolean a = matching instanceof String s && s.length() == 5;

        check(a);
    }
}
