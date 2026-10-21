package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_ARRAYS, Blocker.FRONTEND_OTHER}, exitCode = 4)
class SubjectObserverInvalid {
    static class Observer {
        int cache;
    }

    static class Subject {
        int value;
        final Observer[] observers;
        int count;

        Subject(int capacity) {
            precondition(capacity > 0);
            this.observers = new Observer[capacity];
            this.count = 0;
            this.value = 0;
        }
    }

    @Pure
    @Erased
    static boolean synced(Subject s) {
        reads(everything());
        return 0 <= s.count && s.count <= s.observers.length
                && forall((int i) -> implies(0 <= i && i < s.count,
                    s.observers[i] != null && s.observers[i].cache == s.value));
    }

    static void register(Subject s, Observer o) {
        precondition(synced(s));
        precondition(s.count < s.observers.length);
        modifies(everything());
        postcondition(synced(s));
        postcondition(s.count == old(s.count) + 1 && s.observers[s.count - 1] == o);

        o.cache = s.value;
        s.observers[s.count] = o;
        s.count = s.count + 1;
    }

    static void update(Subject s, int v) {
        precondition(synced(s));
        modifies(everything());
        postcondition(synced(s));
//                    ^^^^^^^^^ Error: postcondition could not be proved
        postcondition(s.value == v && s.count == old(s.count));

        s.value = v;
        int i = 0;
        while (i < s.count - 1) {
            modifies(everything());
            invariant(0 <= i && i <= s.count && s.count <= s.observers.length);
            invariant(s.value == v);
            invariant(forall((int k) -> implies(0 <= k && k < s.count, s.observers[k] != null)));

            s.observers[i].cache = v;
            i = i + 1;
        }
    }
}
