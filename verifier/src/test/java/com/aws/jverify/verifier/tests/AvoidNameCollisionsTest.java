package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(dafnyVerified = 3, dafnyErrors = 0, avoidNameCollisions = true, verifyPrintedDafny = false)
public class AvoidNameCollisionsTest {

    void set(int set, int r_set) {}

    void _test() {
        var _test = 3;
        var a_test = 4;
    }

    class _test {}
    class a_test {}
    
    public int differentReturnValueNames() {
        postcondition(this::predicate);
        postcondition((Integer result) -> result < 3);
        int result;
        int g_result;
        int _g_result;
        return 2;
    }
    
    @Pure
    boolean predicate(int r) {
        return true;
    }

    public class Base {
        public Base() {

        }
        
        public void init_Base() {
        }
        public void _init_Base() {
        }
        public void ctor_Base() {
        }
        public void _ctor_Base() {
        }
    }

    public class Extendee extends Base {
        public Extendee() {
            super();
        }

        public void init_Extendee() {
        }
        public void _init_Extendee() {
        }
        public void ctor_Extendee() {
        }
        public void _ctor_Extendee() {
        }
    }
}