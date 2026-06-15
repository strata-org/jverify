package org.strata.jverify.verifier.tests.javasupport.records;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 2)
class ResolutionErrorsStringMethods {
    static void stringFormatted() {
//              ^ error: Unsupported constant type tag: CLASS
        check("hello %s".formatted("world").length() == 11);
//            ^ warning: missing contract for method 'formatted' in class 'java.lang.String'
//                                 ^ error: new array with initializers is not supported
    }
}
