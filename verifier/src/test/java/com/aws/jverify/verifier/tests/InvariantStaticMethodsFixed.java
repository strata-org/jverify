package com.aws.jverify.verifier.tests;

import com.aws.jverify.Invariant;
import static com.aws.jverify.JVerify.*;

/**
 * Test class to verify that @Invariant methods work correctly with static methods.
 * Before the fix, having static methods in a class with @Invariant would cause:
 * "Error: 'this' is not allowed in a 'static' context"
 * 
 * After the fix, invariants should only be applied to public instance methods,
 * not to static methods.
 */
public class InvariantStaticMethodsFixed {
    
    private int balance;
    
    public InvariantStaticMethodsFixed(int initialBalance) {
        precondition(initialBalance >= 0);
        this.balance = initialBalance;
    }
    
    // Invariant that references instance state
    @Invariant
    public boolean balanceIsNonNegative() {
        return this.balance >= 0;
    }
    
    // Public instance method - should have invariant applied as pre/post condition
    public void deposit(int amount) {
        precondition(amount > 0);
        this.balance += amount;
    }
    
    // Public instance method - should have invariant applied as pre/post condition  
    public boolean withdraw(int amount) {
        precondition(amount > 0);
        if (amount <= this.balance) {
            this.balance -= amount;
            return true;
        }
        return false;
    }
    
    // Static method - should NOT have invariant applied (no 'this' context)
    public static int calculateInterest(int principal, double rate) {
        precondition(principal >= 0);
        precondition(rate >= 0.0);
        return (int) (principal * rate / 100);
    }
    
    // Another static method - should NOT have invariant applied
    public static boolean isValidAmount(int amount) {
        return amount > 0;
    }
    
    // Private method - should NOT have invariant applied (not public)
    private void internalOperation() {
        // This method should not have invariants applied because it's private
    }
    
    // Public getter - should have invariant applied
    public int getBalance() {
        return this.balance;
    }
}
