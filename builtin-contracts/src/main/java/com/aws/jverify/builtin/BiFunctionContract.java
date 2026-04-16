package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import java.util.function.BiFunction;

@Contract(BiFunction.class)
abstract class BiFunctionContract<T, U, R> implements BiFunction<T, U, R> {
    @Pure
    public R apply(T t, U u) {
        throw new ContractException();
    }
}