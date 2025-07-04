package com.aws.jverify.verifier.tests;

import com.aws.jverify.Invariant;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;


import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

/**
 * Test class to verify that @Invariant methods work correctly with static methods.
 * Before the fix, having static methods in a class with @Invariant would cause:
 * "Error: 'this' is not allowed in a 'static' context"
 * 
 * After the fix, invariants should only be applied to public instance methods,
 * not to static methods.
 */
@JVerifyTest(exitCode = 0, dafnyVerified = 10, dafnyErrors = 0)
public class InvariantsAndStaticMethodsInSameClass {
    
    private @Unbounded int balance;

    public InvariantsAndStaticMethodsInSameClass(int initialBalance) {
        precondition(initialBalance >= 0);
        this.balance = initialBalance;
    }
    
    // Invariant that references instance state
    @Pure
    @Invariant
    private boolean balanceIsNonNegative() {
        reads(this); return this.balance >= 0;
    }
    
    // Public instance method - should have invariant applied as pre/post condition
    public void deposit(int amount) {
        precondition(amount > 0);
        modifies(this);
        this.balance += amount;
    }

    // Static method - should NOT have invariant applied
    public static boolean isValidAmount(int amount) {
        return amount > 0;
    }

}
