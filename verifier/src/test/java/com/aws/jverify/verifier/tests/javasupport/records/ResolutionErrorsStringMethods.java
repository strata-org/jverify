package com.aws.jverify.verifier.tests.javasupport.records;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 2)
class ResolutionErrorsStringMethods {
    static void stringFormatted() {
        check("hello %s".formatted("world").length() == 11);
//                                ^ error: String method formatted(java.lang.Object...) is not supported
//                                 ^ error: new array with initializers is not supported
    }
}
