package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import java.io.PrintStream;

@Contract(value = PrintStream.class)
class PrintStreamContracts {
    public void println(String x) {
        throw new ContractException();
    }
}