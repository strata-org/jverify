package com.aws.jverify.verifier.tests.javasupport.localClasses;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef"})
@JVerifyTest(exitCode = 4, dafnyVerified = 25, dafnyErrors = 3, verifyPrintedDafny = true)
public class LocalAndAnonymousClasses {

    public void useAnonymousClasses() {
        class LocalSomethingDoerClass implements SomethingDoer {
            @Override
            public int doSomething(int x, int y) {
                return add(x, y);
            }
        };
        var arg1 = new LocalSomethingDoerClass();
        doSomethingTwice(arg1);
        
        SomethingDoer arg2 = new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return LocalAndAnonymousClasses.this.add(x, y);
            }
        };
        doSomethingTwice(arg2);
        SomethingDoer arg3 = new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return x;
            }
        };
        doSomethingTwice(arg3);
        SomethingDoer arg4 = new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return LocalAndAnonymousClasses.staticAdd(x, y);
            }
        };
        doSomethingTwice(arg4);
        int z = 42;
        SomethingDoer arg5 = new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return z;
            }
        };
        doSomethingTwice(arg5);
        SomethingDoerWithSpec arg6 = new SomethingDoerWithSpec() {
            @Override
            public int doSomething(int x, int y) {
                precondition(x >= y);
                postcondition((int r) -> r == x - y);
                return x - y;
//                     ^^^^^ Error: value does not satisfy the subset constraints of 'int32'
            }
        };
        doSomethingWithSpecTwice(arg6);

        SomethingDoer doer = new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return x;
            }
        };
        SomethingDoer doer2 = new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return x;
            }
        };
        // Important that these values aren't equal,
        // since they aren't in Java semantics,
        // but if we map lambdas to datatype values incorrectly
        // they could be equal Dafny values.
        check(doer != doer2);

        SomeClassMaker arg7 = new SomeClassMaker() {
            @Override
            public SomeClass makeSomething() {
                return new SomeClass();
            }
        };
        makeSomeClass(arg7);
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
                postcondition((int r) -> r == x - y);
                throw new ContractException();
            }
        }
    }
}