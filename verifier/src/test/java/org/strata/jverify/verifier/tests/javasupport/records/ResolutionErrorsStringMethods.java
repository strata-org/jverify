package org.strata.jverify.verifier.tests.javasupport.records;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4)
class ResolutionErrorsStringMethods {
    static void stringFormatted() {
        check("hello %s".formatted("world").length() == 11);
//            ^ warning: missing contract for method 'formatted' in class 'java.lang.String'
//            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: Resolution failed: 'String_length' is not defined
    }
}
