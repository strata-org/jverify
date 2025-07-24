package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.Predicate;

@JVerifyTest(exitCode = 4, dafnyVerified = 5, dafnyErrors = 2)
public class AnonymousClasses {

    public void foo() {
        final Predicate p = new Predicate() {
            @Override
            public boolean test(Object o) {
                return false;
            }
        };
    }
}
