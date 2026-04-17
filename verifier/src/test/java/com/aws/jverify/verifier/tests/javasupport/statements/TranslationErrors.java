package com.aws.jverify.verifier.tests.javasupport.statements;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Nullable;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2)
class TranslationErrors {
    
    void quantifierNeedsLambdaArgument() {
        check(forall((int i) -> i > 0));
        java.util.function.IntPredicate f =
                (int i) -> i > 0;
        check(forall(f));
//                   ^ error: the argument to a forall call must be a lambda
    }
    
    void contractAfterBody(int x) {
        x = x + 3;
        precondition(x == 3);
//                  ^ error: call to contract method must come before the rest of the body
    }
    
    void wrongContractMethod(int x) {
//       ^ error: invariants are not allowed in a method
        invariant(x == 3);
        while(x > 0) {
//      ^ error: preconditions are not allowed in a loop
            precondition(x == 3);
            x = x - 1;
        }
        x = 3;
        while(x > 0) {
//      ^ error: postconditions are not allowed in a loop
            postcondition(x == 3);
            x = x - 1;
        }
    }

    int switchLabeledStatementGroup(int i) {
        var acc = 0;
        var ret = switch (i) {
            case 0:
//          ^ error: switch labeled statement group is not supported
                acc += 100;
                yield 0;
            case 1, 2, 3:
//          ^ error: switch labeled statement group is not supported
//          ^ error: switch labeled statement group is not supported
//          ^ error: switch labeled statement group is not supported
                acc += 200;
                yield i * 2;
            default:
//          ^ error: switch labeled statement group is not supported
                yield -1;
        };
        return acc + ret;
    }

    int switchCaseConstantExpr(int i) {
        return switch (i) {
            case 0, 1 -> 0;
            case 1 + 1 -> 1;
//                 ^ error: non-literal case constant is not supported
            default -> 2;
        };
    }

    @Contract(Integer.class)
    static class IntegerContract {
        @Pure
        public int intValue() {
            throw new ContractException();
        }
    }
    
    int switchCasePattern(Integer i) {
        return switch (i) {
            case Integer ii when ii < 0 -> ii * 3;
//          ^ error: case pattern is not supported
            case Integer ii when 0 < ii -> ii * 2;
//          ^ error: case pattern is not supported
            default -> 0;
        };
    }

    int switchExprBlockBody(int i) {
        return switch (i) {
            case 0 -> 0;
            case 1, 2, 3 -> {
//          ^ error: switch labeled statement group is not supported
//          ^ error: switch labeled statement group is not supported
//                          ^ error: switch rule block is not supported
                var acc = 0;
                for (int j = 0; j < i; j++) {
                    acc += j * j;
                }
                yield acc;
            }
            default -> i;
        };
    }

    int switchThrowBody(int i) {
        return switch (i) {
            case 0 -> throw new RuntimeException();
//                    ^ error: switch rule throw statement is not supported
            case 1, 2 -> -i;
//          ^ error: switch labeled statement group is not supported
            default -> i * i * i;
        };
    }
    
    @Contract(RuntimeException.class)
    static class RuntimeExceptionContract {
        public RuntimeExceptionContract() {
        }
    }

    // This is intentional: primitives aren't nullable in Java.
    static boolean nullablePrimitive(@Nullable int i) {
//                                   ^ error: nullable primitive type is not supported
        return i == 0;
    }

    static boolean nullableBoxed(@Nullable Integer i) {
        return i == 0;
    }

    record IntWrapper(int value) {}

    @SuppressWarnings("unused")
    record IntWrapperWrapper(@Nullable IntWrapper inner) {}

    static boolean nullableRecord(@Nullable IntWrapper w) {
        return w == null;
    }

    static int referenceEquality(
            Object o1, Object o2,
            Integer i,
            IntWrapper w1, IntWrapper w2
    ) {
        if (o1 == o2) {
//             ^ error: '==' is only allowed when at least one operand's type is impure
            return 0;
        } else if (o1 == null) {
            return 1;
        } else if (null == o2) {
            return 2;
        } else if (i == o1) {
//                   ^ error: '==' is only allowed when at least one operand's type is impure
            return 3;
        } else if (w1 == null) {
            return 4;
        } else if (w1 == w2) {
//                    ^ error: '==' is only allowed when at least one operand's type is impure
            return 5;
        }

        return -1;
    }

    record DummyRecord() {}

    static class DummyClass {}

    @SuppressWarnings("ConstantValue")
    static void newInExpression() {
        check(new DummyRecord() != null);

        check(new DummyClass() != null);
    }

    void foo() {}

    void ifWithThrow(int x) {
        if (x > 0) {
            throw new RuntimeException();
//          ^ error: statement JCThrow is not supported
        }
    }
    
    void arrayWithInitializer() {
        int[] arr = {1, 2, 3};
//                  ^ error: new array with initializers is not supported
    }
}
