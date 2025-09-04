package com.aws.jverify.verifier.tests.javasupport.records;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 2)
class ResolutionErrorsStringMethods {
    static void stringFormatted() {
        check("hello %s".formatted("world").length() == 11);
//            ^ warning: missing contract for method 'formatted' in class 'java.lang.String'
//                                 ^ error: new array with initializers is not supported
//            ^ warning: missing contract for method 'length' in class 'java.lang.String'
    }
}
