package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 2)
class TranslationErrors {
    @Pure
    int pureWithMultipleStatements() {
//      ^ error: pure method should have only one statement
        foo();
        return 3;
    }

    @Pure
    void pureWithoutReturn() {        
//       ^ error: pure method should have a return type
        var x = 3;
    }
    
    int multipleReturnNames() {
//      ^ error: ensures clauses may introduce only one return variable name
       postcondition((Integer i) -> i > 0);
       postcondition((Integer j) -> j < 2);
       return 1;
    }
    
    void quantifierNeedsLambdaArgument() {
        java.util.function.Function<Integer, Boolean> f =
//                                 ^ error: JCTypeApply is not supported        
                (Integer i) -> i > 0;
//              ^ error: JCLambda is not supported
        check(forall(f));
//                   ^ error: the argument to a forall call must be a lambda
    }
    
    void contractAfterBody(int x) {
        x = x + 3;
        precondition(x == 3);
//                  ^ error: call to JVerify header method precondition is not allowed after non-header statement
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
//                ^ error: switch labeled statement group is not supported
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

    int switchCaseConstantExpr(int i) {
        return switch (i) {
            case 0, 1 -> 0;
            case 1 + 1 -> 1;
//                 ^ error: non-literal case constant is not supported
            default -> 2;
        };
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
            case 0 -> throw new RuntimeException("");
//                    ^ error: switch rule throw statement is not supported
            case 1, 2 -> -i;
            default -> i * i * i;
        };
    }
    
    void foo() {}
}
