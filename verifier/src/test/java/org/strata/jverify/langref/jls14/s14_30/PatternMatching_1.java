package org.strata.jverify.langref.jls14.s14_30;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.30.2 -- Pattern Matching: a value matches a type pattern iff it is non-null and an instance of the type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_30.PatternMatching_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class PatternMatching_1 {
    static void run() {

        Object matching = "hello";

        // A non-null instance matches the type pattern and the binding is in scope.
        boolean a = matching instanceof String s && s.length() == 5;

        check(a);
    }
}
