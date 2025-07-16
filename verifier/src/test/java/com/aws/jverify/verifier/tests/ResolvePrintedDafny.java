package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 9, dafnyErrors = 1, resolvePrintedDafny = true)
public class ResolvePrintedDafny {

    void function(int function) {}
    void set(int set) {}
    void map(int map) {}
    
    void _test() {
        var _test = 3;
    }
    
    class _test {}
}
