package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import java.io.PrintStream;

@Contract(System.class)
class SystemContract {
    public static final PrintStream out = SystemContractHelper.createPrintStream();
    
}