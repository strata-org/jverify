package org.strata.jverify.langref.jls6.s6_7;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.7 -- Fully Qualified Names and Canonical Names: a type may be
// referred to by its fully qualified name; here `java.lang.Integer` is used
// without an import declaration.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_7.FullyQualified_1#run")
class FullyQualified_1 {
    static void run() {
        java.lang.Integer i = 5;
        check(i.intValue() == 5);
    }
}
