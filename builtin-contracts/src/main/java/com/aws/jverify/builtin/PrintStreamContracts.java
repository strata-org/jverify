package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import java.io.PrintStream;

@Contract(value = PrintStream.class)
class PrintStreamContracts {
    public void println(String x) {
        throw new ContractException();
    }
}