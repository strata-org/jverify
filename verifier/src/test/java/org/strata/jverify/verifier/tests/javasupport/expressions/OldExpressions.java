package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// old(e) evaluates e in the method's pre-state.
@JVerifyTest(exitCode = 4, methodsVerified = 10, errorCount = 2)
class OldExpressions {
    static class Rejected extends RuntimeException {}
//  ^ warning: missing contract for method '<init>' in class 'java.lang.RuntimeException'

    int x;
    long total;
    boolean flag;
    short s;

    void inc() {
        precondition(x < 1000);
        modifies(this);
        postcondition(() -> x == old(x) + 1);
        x = x + 1;
    }

    void incWrong() {
        precondition(x < 1000);
        modifies(this);
        postcondition(() -> x == old(x) + 2);
//      ^ Error: postcondition could not be proved
        x = x + 1;
    }

    // A caller relies on inc's promise.
    static void callsInc(OldExpressions o) {
        precondition(o.x == 5);
        o.inc();
        check(o.x == 6);
    }

    void addLong(long n) {
        precondition(0 <= n && n < 1000 && 0 <= total && total < 1000);
        modifies(this);
        postcondition(() -> total == old(total) + n);
        total = total + n;
    }

    void toggle() {
        modifies(this);
        postcondition(() -> flag == !old(flag));
        flag = !flag;
    }

    void bumpShort() {
        precondition(s < 100);
        modifies(this);
        postcondition(() -> s == old(s) + 1);
        s++;
    }

    // old in a body assertion and in a loop invariant.
    void inBody(int n) {
        precondition(0 <= n && n < 100 && 0 <= x && x < 100);
        modifies(this);
        x = x + 1;
        check(x == old(x) + 1);
        int i = 0;
        while (i < n) {
            invariant(0 <= i && i <= n && x == old(x) + 1);
            i = i + 1;
        }
    }

    // The exceptional postcondition says a rejected deposit changes nothing.
    void deposit(int amount) throws Rejected {
        precondition(0 <= x && x < 100000 && amount < 100000);
        modifies(this);
        throwsOn(amount <= 0).postcondition((Rejected e) -> x == old(x));
        postcondition(() -> x == old(x) + amount);
        if (amount <= 0) {
            throw new Rejected();
        }
        x = x + amount;
    }

    // Changes x before rejecting, so the exceptional postcondition fails.
    void depositChangesFirst(int amount) throws Rejected {
        precondition(0 <= x && x < 100000 && amount < 100000);
        modifies(this);
        throwsOn(amount <= 0).postcondition((Rejected e) -> x == old(x));
//      ^ Error: postcondition could not be proved
        postcondition(() -> x == old(x) + amount);
        x = x + 1;
        if (amount <= 0) {
            throw new Rejected();
        }
        x = x - 1 + amount;
    }

    static void bumpElement(int[] a, int i) {
        precondition(0 <= i && i < a.length && a[i] < 1000);
        modifies(a);
        postcondition(() -> a[i] == old(a[i]) + 1);
        a[i] = a[i] + 1;
    }

    void inPrecondition() {
//  ^ warning: instance method 'inPrecondition' was not verified: old(...) is not allowed in a precondition: it is already evaluated in the method's pre-state
        precondition(old(x) == 0);
        modifies(this);
        x = 1;
    }

    void inThrowsOnGuard() throws Rejected {
//  ^ warning: instance method 'inThrowsOnGuard' was not verified: old(...) is not allowed in a throwsOn guard: it is already evaluated in the method's pre-state
        modifies(this);
        throwsOn(old(x) < 0).postcondition((Rejected e) -> true);
        if (x < 0) {
            throw new Rejected();
        }
        x = 1;
    }
}
