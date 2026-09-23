package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Nullable;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

import static org.strata.jverify.JVerify.*;

/**
 * Multi-blocker fixture (was mislabelled FRONTEND_DIAGNOSTIC). The diagnostics it
 * pins are now EMITTED -- misplaced contract calls, invariants in a method,
 * contract methods in a loop, restricted {@code ==}, array-with-initializer -- so
 * the diagnostics port is not what blocks it. What remains:
 *
 * <ul>
 * <li>FRONTEND_OTHER: switch EXPRESSIONS are refused whole-method
 * ({@code Unsupported expression: JCSwitchExpression}); {@code forall} of a
 * non-lambda and a {@code throw} in an {@code if} demote to method warnings with
 * different wording than the Dafny-era per-construct errors.
 * <li>STRATA_NULLABLE: {@code @Nullable} parameters compared with null lower to
 * {@code Unsupported constant type tag: BOT} until Option synthesis lands.
 * </ul>
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_NULLABLE}, exitCode = 2)
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
        x = 3;
        while(x > 0) {
//      ^ error: throwsOn cases are not allowed in a loop
            throwsOn(x == 3);
            x = x - 1;
        }
    }

    // Switch EXPRESSIONS are refused whole-method now (Unsupported expression:
    // JCSwitchExpression) rather than per-case, so these methods carry one
    // method-level warning instead of the Dafny-era per-label errors. Switch
    // translation is a FRONTEND_OTHER gap, not a diagnostic to restore.
    int switchLabeledStatementGroup(int i) {
//      ^ warning: instance method 'switchLabeledStatementGroup' was not verified: Unsupported expression: JCSwitchExpression
        var acc = 0;
        var ret = switch (i) {
            case 0:
                acc += 100;
                yield 0;
            case 1, 2, 3:
                acc += 200;
                yield i * 2;
            default:
                yield -1;
        };
        return acc + ret;
    }

    // `case 1 + 1 ->` verifies now: the pipeline evaluates a constant case
    // expression rather than refusing it, so the Dafny-era
    // "non-literal case constant is not supported" expectation was stale and is
    // dropped.
    int switchCaseConstantExpr(int i) {
        return switch (i) {
            case 0, 1 -> 0;
            case 1 + 1 -> 1;
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
//      ^ warning: instance method 'switchCasePattern' was not verified: Unsupported expression: JCSwitchExpression
        return switch (i) {
            case Integer ii when ii < 0 -> ii * 3;
            case Integer ii when 0 < ii -> ii * 2;
            default -> 0;
        };
    }

    int switchExprBlockBody(int i) {
//      ^ warning: instance method 'switchExprBlockBody' was not verified: Unsupported expression: JCSwitchExpression
        return switch (i) {
            case 0 -> 0;
            case 1, 2, 3 -> {
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
//      ^ warning: instance method 'switchThrowBody' was not verified: Unsupported expression: JCSwitchExpression
        return switch (i) {
            case 0 -> throw new RuntimeException();
//                    ^ error: switch rule throw statement is not supported
            case 1, 2 -> -i;
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
//                                          ^ error: Unsupported constant type tag: BOT
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
//       ^ warning: instance method 'ifWithThrow' was not verified: throw requires the enclosing method to declare it
        if (x > 0) {
            throw new RuntimeException();
        }
    }
    
    void arrayWithInitializer() {
        int[] arr = {1, 2, 3};
//                  ^ error: new array with initializers is not supported
    }
}
