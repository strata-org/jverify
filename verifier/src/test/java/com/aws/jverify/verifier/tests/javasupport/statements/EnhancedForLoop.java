package com.aws.jverify.verifier.tests.javasupport.statements;

import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;

import static com.aws.jverify.JVerify.invariant;

@SuppressWarnings({"ConditionalBreakInInfiniteLoop", "StatementWithEmptyBody", "ConstantValue"})
@JVerifyTest(dafnyVerified = 4, dafnyErrors = 0, useBuiltinContracts = true)
class EnhancedForLoop {
    void enhancedForWithIterable() {
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

    void enhancedForWithArray(int[] numbersArray) {
        @Unbounded int sum = 0;
        for (var x : numbersArray) {
            invariant(true);
            sum += x;
        }
        // For proving things about enhanced for loops, we need access to the history of the iterator
        // It seems easier to use a non-enhanced for loop then
        // check(sum == 6);
    }
}
