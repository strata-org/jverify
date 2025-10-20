package com.aws.jverify.verifier.tests.javasupport.statements;

import com.aws.jverify.testengine.JVerifyTest;

import java.lang.foreign.SymbolLookup;
import java.util.Iterator;
import java.util.List;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.invariant;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
@JVerifyTest(dafnyVerified = 16, dafnyErrors = 0, useBuiltinContracts = true)
class EnhancedForLoop {
    void enhancedFor() {
        var numbers = List.of(1,2,3);
        var sum = 0;
        for (var x : numbers) {
            invariant(true);
            sum += x;
        }
        check(sum == 6);
    }
}
