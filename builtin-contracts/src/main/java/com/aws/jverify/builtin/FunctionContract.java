package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import java.util.function.Function;
import static com.aws.jverify.JVerify.*;

@Contract(value = Function.class, pure = true)
abstract class FunctionContract<T, R> implements Function<T, R> {
    @Pure
    public R apply(T t) {
        precondition(isAbstractBoolean());
        throw new ContractException();
    }
}