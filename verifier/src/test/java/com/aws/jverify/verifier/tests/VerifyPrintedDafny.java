package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0, verifyPrintedDafny = true)
public class VerifyPrintedDafny {

    void function(int function) {}
    void set(int set) {}
    void map(int map) {}
    
    void _test() {
        var _test = 3;
    }
    
    class _test {}
}
