package org.strata.jverify.langref.jls6.s6_7;

import static org.strata.jverify.JVerify.*;

// JLS 6.7 -- Fully Qualified Names and Canonical Names: a type may be
// referred to by its fully qualified name; here `java.lang.Integer` is used
// without an import declaration.
class FullyQualified_1 {
    static void run() {
        java.lang.Integer i = 5;
        check(i.intValue() == 5);
    }
}
