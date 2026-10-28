package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Transparent;
import static org.strata.jverify.JVerify.*;

@Contract(Boolean.class)
class BooleanContract {

    public static Boolean valueOf(boolean b) {
        postcondition((Boolean boxed) -> boxed.booleanValue() == b);
        throw new ContractException();
    }

    @Transparent
    public boolean booleanValue() {
        throw new ContractException();
    }
}