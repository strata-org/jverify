package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;
import java.util.function.IntFunction;

@JVerifyTest(dafnyVerified = 4, dafnyErrors = 0, resolvePrintedDafny = true)
public class ResolveGeneratedDafny {
    
    void overloads(List<? extends Object> elements) {}
    void overloads(int x) {}
    
    void lambdaCanBeResolved() {
        foo(i -> i < 10 ? i + 2 : i);
    }

    void foo(IntFunction<Integer> function) {}
    
    // TODO, test reserved names such as map and function
    // TODO, test identifier starting with underscore
}
