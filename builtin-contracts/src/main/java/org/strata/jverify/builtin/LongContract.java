package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import static org.strata.jverify.JVerify.*;

@Contract(Long.class)
class LongContract {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;

    @Pure
    public static Long valueOf(long l) {
        postcondition((Long b) -> b.longValue() == l);
        throw new ContractException();
    }

    @Pure
    public long longValue() {
        throw new ContractException();
    }
}