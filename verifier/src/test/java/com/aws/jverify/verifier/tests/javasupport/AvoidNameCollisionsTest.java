package com.aws.jverify.verifier.tests.javasupport;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.IntPredicate;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(dafnyVerified = 3, dafnyErrors = 0, verifyPrintedDafny = true)
public class AvoidNameCollisionsTest {

    void set(int set, int r_set) {}
    void function(int function) {}
    void set(int set) {}

    class _test {}

    class a_test {}

    void _test() {
        var _test = 3;
        var a_test = 4;
    }
    
    public int differentReturnValueNames() {
        postcondition((IntPredicate)this::predicate);
        postcondition((int result) -> result < 3);
        int result;
        int g_result;
        boolean _g_result = this.map(3);
        return 2;
    }
    
    @Pure
    boolean predicate(int r) {
        return true;
    }

    @Pure
    boolean map(int r) {return true;}

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
    
    public int m() {
        int r;
        r = 0;
        return r;
    }
}