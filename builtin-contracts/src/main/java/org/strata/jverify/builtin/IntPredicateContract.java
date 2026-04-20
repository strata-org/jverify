package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import java.util.function.IntPredicate;
import static org.strata.jverify.JVerify.*;

@Contract
abstract class IntPredicateContract implements IntPredicate {
    
    @Pure
    public boolean test(int value) {
        precondition(isAbstractBoolean());
        throw new ContractException();
    }
}