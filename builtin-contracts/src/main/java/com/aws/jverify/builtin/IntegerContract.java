package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import static com.aws.jverify.JVerify.*;

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