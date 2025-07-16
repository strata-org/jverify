package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.IntFunction;
import java.util.function.Predicate;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@JVerifyTest(exitCode = 4, dafnyVerified = 9, dafnyErrors = 3)
public class Lambdas {

//    void lambdaForGenericInterfaces() {
//        foo(i -> i < 10 ? i + 2 : i);
//        bar(i -> i > 10);
//    }

//    <T> void lambdaForGenericMethod() {
//        zaz((T e) -> true);
//    }

    void foo(IntFunction<Integer> function) {}
    void bar(Predicate<Integer> function) {}
    <T> void zaz(Predicate<T> function) {}
    
    public void useLambdas() {
        doSomethingTwice((x, y) -> x);
        doSomethingTwice((x, y) -> Lambdas.staticAdd(x, y));
        int z = 42;
        doSomethingTwice((x, y) -> z);

        //doSomethingTwice(this::add);
        //doSomethingTwice(Lambdas::staticAdd);
        doSomethingWithSpecTwice((x, y) -> {
            precondition(x >= y);
            postcondition((Integer r) -> r == x - y);
            return x - y;
//                 ^^^^^ Error: value does not satisfy the subset constraints of 'int32'
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

        //makeSomeClass(SomeClass::new);
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

