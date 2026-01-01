package com.aws.jverify.verifier.tests.javasupport.lambdas;

import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@SuppressWarnings({"FieldMayBeFinal", "Convert2MethodRef", "ConstantValue"})
@JVerifyTest(exitCode = 4, javaVerified = 96, javaErrors = 3, verifyPrintedDafny = true)
public class Lambdas {
    private static final SomethingDoer containsMethodReference = Lambdas::staticDoSomething;
    private static final SomethingDoer containsLambda = (int x, int y) -> staticDoSomething(x, y);
    private static final SomethingDoer containsInnerClass = new SomethingDoer() {
        @Override
        public int doSomething(int x, int y) {
            return 0;
        }
    };
    
    @Pure
    static int staticDoSomething(int x, int y) {
        return 3;
    }

    // TODO: bring back once we support static fields
//    private static int STATIC_FIELD = 100;
    private final int instanceField = 50;
    
    static void lambdasInStaticMethod() {
        check(doSomethingTwiceStatic((x, y) -> x) == 2);

//        doSomethingTwice((x, y) -> STATIC_FIELD);
        
        doSomethingTwiceStatic((x, y) -> Lambdas.staticAdd(x, y));

        doSomethingTwiceStatic((x, y) -> staticAdd(x,y));
    }
    
    void classCaptures() {
        doSomethingTwice((x, y) -> x);

        check(instanceField == doSomethingTwice((x, y) -> instanceField));
//        doSomethingTwice((x, y) -> STATIC_FIELD);

        doSomethingTwice((x, y) -> this.add(x,y));
        doSomethingTwice((x, y) -> Lambdas.staticAdd(x, y));

        doSomethingTwice((x, y) -> add(x,y));
        doSomethingTwice((x, y) -> staticAdd(x,y));
    }

    void localCaptures() {
        int z = 42;
        final int finalZ = 43;
        check(z == doSomethingTwice((x, y) -> z));
        check(finalZ == doSomethingTwice((x, y) -> finalZ));
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

    Lambdas methodReferencesPostCondition() {
        postcondition(Lambdas::instancePredicate);
        return this;
    }
    
    void methodReferences() {
        postcondition(instancePredicate());

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
    
    void pure() {
        pureFunctionUser((Integer x) -> x);
    }

    @Pure
    static int doSomethingTwiceStatic(SomethingDoer doer) {
        var y = doer.doSomething(1, 2);
        return doer.doSomething(2, y);
    }

    @Pure
    int doSomethingTwice(SomethingDoer doer) {
        var y = doer.doSomething(1, 2);
        return doer.doSomething(2, y);
    }
    @Pure
    int doSomethingTwiceWithLambdas(SomethingDoerWithLambdas doer) {
        var y = doer.doSomething(this, 1, 2);
        return doer.doSomething(this, 2, y);
    }
    
    @Verify(false)
    void doSomethingWithSpecTwice(SomethingDoerWithSpec doer) {
        var y = doer.doSomething(2, 1);
        var z = doer.doSomething(2, y);
    }

    @Pure
    public boolean instancePredicate() {
        return true;
    }

    @Pure
    public int add(int x, int y) {
        assume(false);
        return x + y;
    }

    @Pure
    public static int staticAdd(int x, int y) {
        assume(false);
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
    
    @Verify(false)
    void pureFunctionUser(PureFunction<Integer, Integer> pureFunction) {
        
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
    @Pure
    @EmptyContract
    int doSomething(Lambdas lambdas, int x, int y);
}

interface SomethingDoer {
    @Pure
    @EmptyContract
    int doSomething(int x, int y);
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

interface PureFunction<I, O> {
    @Pure
    O apply(I input);
}
