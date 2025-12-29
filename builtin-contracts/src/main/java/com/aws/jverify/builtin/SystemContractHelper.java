package com.aws.jverify.builtin;

import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import java.io.PrintStream;

class SystemContractHelper {
    @Verify(false)
    @Pure
    public static PrintStream createPrintStream() {
        throw new ContractException();
    }
}