package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@JVerifyTest(exitCode = 4, dafnyVerified = 10, dafnyErrors = 1)
public class Lambdas {

    public void useLambdas() {
        doSomethingTwice((x, y) -> x);
        doSomethingTwice((x, y) -> Lambdas.staticAdd(x, y));
        int z = 42;
        doSomethingTwice((x, y) -> z);

        doSomethingTwice(this::add);
        doSomethingTwice(Lambdas::staticAdd);
        doSomethingWithSpecTwice((x, y) -> {
//                               ^ Error: a precondition for this call could not be proved
//                               ^^^^^^^^^^^ Error: the method must provide an equal or more detailed postcondition than in its parent trait
            precondition(x >= y);
//                       ^^^^^^ Related location: this is the precondition that could not be proved
            postcondition((Integer r) -> r == x - y);
            return x - y;
//                 ^ Error: value does not satisfy the subset constraints of 'int32'
        });

        SomethingDoer doer = (x, y) -> {
            return x;
        };
        SomethingDoer doer2 = (x, y) -> {
            return x;
        };
        // Important that these values aren't equal,
        // since they aren't in Java semantics,
        // but if we map lambdas to datatype values incorrectly
        // they could be equal Dafny values.
        check(doer != doer2);

        makeSomeClass(SomeClass::new);
    }

    public void doSomethingTwice(SomethingDoer doer) {
        var y = doer.doSomething(1, 2);
        var z = doer.doSomething(2, y);
    }

    public void doSomethingWithSpecTwice(SomethingDoerWithSpec doer) {
        var y = doer.doSomething(2, 1);
        var z = doer.doSomething(2, y);
    }

    public int add(int x, int y) {
        return x + y;
//             ^^^^^ Error: value does not satisfy the subset constraints of 'int32'
    }

    public static int staticAdd(int x, int y) {
        return x + y;
//             ^^^^^ Error: value does not satisfy the subset constraints of 'int32'
    }

    public void makeSomeClass(SomeClassMaker maker) {
        var sc = maker.makeSomething();
    }
}

class SomeClass {
    public SomeClass() {

    }
}

interface SomeClassMaker {
    SomeClass makeSomething();

    @Contract
    class SomeClassMakerContract implements SomeClassMaker {
        @Override
        public SomeClass makeSomething() {
            throw new ContractException();
        }
    }
}

interface SomethingDoer {
    int doSomething(int x, int y);

    @Contract
    class SomethingDoerContract implements SomethingDoer {

        @Override
        public int doSomething(int x, int y) {
            throw new ContractException();
        }
    }
}

interface SomethingDoerWithSpec {
    int doSomething(int x, int y);

    @Contract
    class SomethingDoerWithSpecContract implements SomethingDoerWithSpec {
        // TODO: Can't currently put @Nat on the return type because there's
        // currently no way to indicate that on the lambda expression
        // since it doesn't declare a return type anywhere.
        @Override
        public int doSomething(int x, int y) {
            precondition(x >= y);
            postcondition((Integer r) -> r == x - y);
            throw new ContractException();
        }
    }
}