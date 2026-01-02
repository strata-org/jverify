package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import static com.aws.jverify.JVerify.*;

@Contract
class IntFunctionContract<R> implements java.util.function.IntFunction<R> {
    @Pure
    public R apply(int value) {
        precondition(isAbstractBoolean());
        throw new ContractException();
    }
}