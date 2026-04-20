package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import java.util.function.BiFunction;

@Contract(BiFunction.class)
abstract class BiFunctionContract<T, U, R> implements BiFunction<T, U, R> {
    @Pure
    public R apply(T t, U u) {
        throw new ContractException();
    }
}