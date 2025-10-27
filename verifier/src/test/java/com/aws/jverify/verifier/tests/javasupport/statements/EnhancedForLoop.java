package com.aws.jverify.verifier.tests.javasupport.statements;

import com.aws.jverify.JVerify;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.Iterator;
import java.util.List;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.invariant;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
@JVerifyTest(dafnyVerified = 3, dafnyErrors = 0, useBuiltinContracts = true)
class EnhancedForLoop {
    void enhancedFor() {
        var numbers = List.of(1, 2, 3);
        @Unbounded int sum = 0;
        for (var x : numbers) {
            invariant(true);
            sum += x;
        }
        // For proving things about enhanced for loops, we need access to the history of the iterator
        // It seems easier to use a non-enhanced for loop then
        // check(sum == 6);
    }
}
