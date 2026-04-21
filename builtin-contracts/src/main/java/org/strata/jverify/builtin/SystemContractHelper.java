package org.strata.jverify.builtin;

import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import java.io.PrintStream;

class SystemContractHelper {
    @Verify(false)
    @Pure
    public static PrintStream createPrintStream() {
        throw new ContractException();
    }
}