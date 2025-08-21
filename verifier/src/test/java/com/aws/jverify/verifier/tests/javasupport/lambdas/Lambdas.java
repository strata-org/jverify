package com.aws.jverify.verifier.tests.javasupport.lambdas;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@SuppressWarnings({"FieldMayBeFinal", "Convert2MethodRef", "ConstantValue"})
@JVerifyTest(exitCode = 4, dafnyVerified = 72, dafnyErrors = 3, verifyPrintedDafny = true)
public class Lambdas {

    // TODO: bring back once we support static fields
//    private static int STATIC_FIELD = 100;
    private int instanceField = 50;
    
    void classCaptures() {
        doSomethingTwice((x, y) -> x);

        doSomethingTwice((x, y) -> instanceField);
//        doSomethingTwice((x, y) -> STATIC_FIELD);

        doSomethingTwice((x, y) -> this.add(x,y));
        doSomethingTwice((x, y) -> Lambdas.staticAdd(x, y));

        doSomethingTwice((x, y) -> add(x,y));
        doSomethingTwice((x, y) -> staticAdd(x,y));
    }

    void localCaptures() {
        int z = 42;
        final int finalZ = 43;
        doSomethingTwice((x, y) -> z);
        doSomethingTwice((x, y) -> finalZ);
    }

    void lambdaWithContract() {
        doSomethingWithSpecTwice((x, y) -> {
            precondition(x >= y);
            postcondition((int r) -> r == x - y);
            return x - y;
//                 ^^^^^ Error: value does not satisfy the subset constraints of 'int32'
        });
    }

    void referenceEquality() {
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
    }

    void methodReferences() {
        doSomethingTwice(this::add);
        doSomethingTwice(Lambdas::staticAdd);
        doSomethingTwiceWithLambdas(Lambdas::add);
        makeSomeClass(SomeClass::new);
        makeSomeInnerClass(SomeClass.SomeInnerClass::new);
    }

    void blockLocals() {
        int outerLocal = 1;
        {
            int blockLocal = 2;

            SomethingDoer lambda = (x, y) -> outerLocal + blockLocal;
//                                           ^^^^^^^^^^^^^^^^^^^^^^^ Error: value does not satisfy the subset constraints of 'int32'
            var z = lambda.doSomething(1,2);
        }
    }

    void nestedLambda() {
        int level1 = 1;

        IntToInt outer = (x) -> {
            int level2 = 2;
            SomethingDoer inner = (x2, y2) -> level1 + level2 + x + x2 + y2; 
//                                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: value does not satisfy the subset constraints of 'int32'

            return inner.doSomething(1,2);
        };
        var result = outer.doSomething(1);

        // TODO lambda result checks, for this and for the other lambdas in the file, should be added in a follow-up 
        // These also need fixes.
//        check(result == 1 + 2 + 1 + 1 + 2);
    }
    
    @Verify(false)
    void doSomethingTwice(SomethingDoer doer) {
        var y = doer.doSomething(1, 2);
        var z = doer.doSomething(2, y);
    }
    @Verify(false)
    void doSomethingTwiceWithLambdas(SomethingDoerWithLambdas doer) {
        var y = doer.doSomething(this, 1, 2);
        var z = doer.doSomething(this, 2, y);
    }
    
    @Verify(false)
    void doSomethingWithSpecTwice(SomethingDoerWithSpec doer) {
        var y = doer.doSomething(2, 1);
        var z = doer.doSomething(2, y);
    }

    @Verify(false)
    public int add(int x, int y) {
        return x + y;
    }

    @Verify(false)
    public static int staticAdd(int x, int y) {
        return x + y;
    }

    @Verify(false)
    void makeSomeClass(SomeClassMaker maker) {
        var sc = maker.makeSomething();
    }
    @Verify(false)
    void makeSomeInnerClass(SomeInnerClassMaker maker) {
        var sc = maker.makeSomething();
    }
}

class SomeClass {
    public SomeClass() {

    }
    
    public static class SomeInnerClass {
        public SomeInnerClass() {
        }
    }
}

interface SomeInnerClassMaker {
    SomeClass.SomeInnerClass makeSomething();

    @Contract
    class SomeInnerClassMakerContract implements SomeInnerClassMaker {
        @Override
        public SomeClass.SomeInnerClass makeSomething() {
            throw new ContractException();
        }
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

interface SomethingDoerWithLambdas {
    int doSomething(Lambdas lambdas, int x, int y);

    @Contract
    class SomethingDoerWithLambdasContract implements SomethingDoerWithLambdas {

        @Override
        public int doSomething(Lambdas lambdas, int x, int y) {
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
