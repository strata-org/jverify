package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import static org.strata.jverify.JVerify.*;

@Contract(Integer.class)
class IntegerContract {
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int MIN_VALUE = 0x80000000;

    @Pure
    public static int sum(int a, int b) {
        precondition(!IntegerContractHelper.sumWillOverflow(a, b));
        return a + b;
    }
    
    @Pure
    public static Integer valueOf(int i) {
        postcondition((Integer b) -> b.intValue() == i);
        throw new ContractException();
    }

    @Pure
    public int intValue() {
        throw new ContractException();
    }
}