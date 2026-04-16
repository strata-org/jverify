package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import static com.aws.jverify.JVerify.*;

@Contract(Boolean.class)
class BooleanContract {

    @Pure
    public static Boolean valueOf(boolean b) {
        postcondition((Boolean boxed) -> boxed.booleanValue() == b);
        throw new ContractException();
    }

    @Pure
    public boolean booleanValue() {
        throw new ContractException();
    }
}