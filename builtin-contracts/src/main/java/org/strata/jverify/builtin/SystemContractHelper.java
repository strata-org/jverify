package org.strata.jverify.builtin;

import org.strata.jverify.ContractException;
import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;
import java.io.PrintStream;

class SystemContractHelper {
    @Verify(false)
    @Transparent
    public static PrintStream createPrintStream() {
        throw new ContractException();
    }
}