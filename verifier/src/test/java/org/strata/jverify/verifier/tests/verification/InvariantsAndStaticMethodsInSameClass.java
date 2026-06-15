package org.strata.jverify.verifier.tests.verification;

import org.strata.jverify.Invariant;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;


import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Test class to verify that @Invariant methods work correctly with static methods.
 * Before the fix, having static methods in a class with @Invariant would cause:
 * "Error: 'this' is not allowed in a 'static' context"
 * 
 * After the fix, invariants should only be applied to public instance methods,
 * not to static methods.
 */
@JVerifyTest(methodsVerified = 2, errorCount = 0)
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
        reads(this); 
        return this.balance >= 0;
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
