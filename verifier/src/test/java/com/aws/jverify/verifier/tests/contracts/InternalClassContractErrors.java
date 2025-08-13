package com.aws.jverify.verifier.tests.contracts;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 2)
public class InternalClassContractErrors {
    static abstract class Foo {
        abstract void bar(int x);
    }

    @Contract
    static class HasNoTarget {}
//         ^ error: could not find a target for @Contract class 'InternalClassContractErrors$HasNoTarget'

    @Contract
    static class FooContract1 extends Foo {
        void bar(int x) {
            check(x > 0);
        }

        void unusedExternalContract() {
//           ^ error: method 'unusedExternalContract' is part of a @Contract class, but its signature does not match any method from the contractee
            postcondition(false);
        }

        static void unusedStaticExternalContract() {
//                  ^ error: method 'unusedStaticExternalContract' is part of a @Contract class, but its signature does not match any method from the contractee
            postcondition(false);
        }
    }

    @Contract(Foo.class)
//  ^ error: class 'Foo' must not be referenced by more than one @Contract annotation
    static class FooContract2 extends Foo {
        void bar(int x) {
            check(x < 0);
        }
    }

    static class ConcreteClass {}

    @Contract(ConcreteClass.class)
//  ^ error: class 'ConcreteClass' must not have an externally defined contract because all its contracts can be defined internally
    static class ConcreteClassContract {}
}