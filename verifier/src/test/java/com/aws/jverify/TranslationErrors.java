package com.aws.jverify;

import static com.aws.jverify.JVerify.*;

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
    
    void foo() {}
}
