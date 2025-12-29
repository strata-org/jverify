package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import java.util.function.IntPredicate;
import static com.aws.jverify.JVerify.*;

@Contract
abstract class IntPredicateContract implements IntPredicate {
    
    @Pure
    public boolean test(int value) {
        precondition(isAbstractBoolean());
        throw new ContractException();
    }
}