package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import java.io.PrintStream;

@Contract(System.class)
class SystemContract {
    public static final PrintStream out = SystemContractHelper.createPrintStream();
    
}