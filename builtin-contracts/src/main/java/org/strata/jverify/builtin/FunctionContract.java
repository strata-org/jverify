package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import java.util.function.Function;
import static org.strata.jverify.JVerify.*;

@Contract(value = Function.class, pure = true)
abstract class FunctionContract<T, R> implements Function<T, R> {
    @Pure
    public R apply(T t) {
        precondition(isAbstractBoolean());
        throw new ContractException();
    }
}