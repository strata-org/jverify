package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import static org.strata.jverify.JVerify.*;

@Contract
class IntFunctionContract<R> implements java.util.function.IntFunction<R> {
    @Pure
    public R apply(int value) {
        precondition(isAbstractBoolean());
        throw new ContractException();
    }
}