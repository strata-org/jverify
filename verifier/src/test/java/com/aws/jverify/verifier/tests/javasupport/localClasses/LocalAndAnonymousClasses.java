package com.aws.jverify.verifier.tests.javasupport.localClasses;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef", "ConstantValue", "NewObjectEquality"})
@JVerifyTest(exitCode = 4, dafnyVerified = 43, dafnyErrors = 4, verifyPrintedDafny = true)
public class LocalAndAnonymousClasses {

    void noCaptures() {
        class LocalSomethingDoerClass implements SomethingDoer {
            @Override
            public int doSomething(int x, int y) {
                return add(x, y);
            }
        };
        var arg1 = new LocalSomethingDoerClass();
        doSomethingTwice(arg1);
    }
    
    public void classCaptures() {
        doSomethingTwice(new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return LocalAndAnonymousClasses.this.add(x, y);
            }
        });
        doSomethingTwice(new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return x;
            }
        });
        SomethingDoer arg4 = new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return LocalAndAnonymousClasses.staticAdd(x, y);
            }
        };
        doSomethingTwice(arg4);
    }
    
    void localCaptures() {
        int z = 42;
        doSomethingTwice(new SomethingDoer() {
            @Override
            public int doSomething(int x, int y) {
                return z;
            }
        });
    }
    
    void withContract() {
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
    }
    
    void referenceEquality() {
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
    }

    void nestedAnonymousClasses() {
        int level1 = 1;

        IntToInt outer = new IntToInt() {
            @Override
            public int doSomething(int x) {
                int level2 = 2;
                SomethingDoer inner = new SomethingDoer() {
                    @Override
                    public int doSomething(int x2, int y2) {
                        var level3 = 3;
                        return level1 + level2 + level3 + x + x2 + y2;
//                             ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: value does not satisfy the subset constraints of 'int32'
                    }
                };

                return inner.doSomething(1,2);
            }
        };
        var result = outer.doSomething(1);

        // TODO closure result checks, for this and for the other closures in the file, should be added in a follow-up 
        // These also need fixes.
//        check(result == 1 + 2 + 3 + 1 + 1 + 2);
    }


    void doSomethingTwice(SomethingDoer doer) {
        var y = doer.doSomething(1, 2);
        var z = doer.doSomething(2, y);
    }

    void doSomethingWithSpecTwice(SomethingDoerWithSpec doer) {
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

    interface IntToInt {
        int doSomething(int x);

        @Contract
        class IntToIntContract implements IntToInt {

            @Override
            public int doSomething(int x) {
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
