// TEST: exitCode=0 dafnyVerified=4 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;

@JVerifyTest
class Constructors {
    private int f;
    private int g = 22;
    private int h = 33;
    public Constructors() {
        postcondition(this.g==22); // Value of g is set by the default initializer
        postcondition(this.f==3);  // Value of f is set by the constructor
        postcondition(this.h==4);  // Value of h is set by the default initializer then changed by the constructor
        f = 3;
        h = 4;
    }


    public Constructors(int f_) {
        postcondition(this.f==f_ && this.g==22 && this.h==33);
        f = f_;
    }

    public Constructors(int f_, int g_) {
        postcondition(this.f==f_ && this.g==g_ && this.h==33);
        f = f_;
    }

    public Constructors(int f_, int g_, int h_) {
        postcondition(this.f==f_ && this.g==g_ && this.h==h_);
        f = f_;
        g = g_;
        h = h_;
    }

    @Pure
    public int getF() { reads(this); return f; }
    @Pure
    public int getG() { reads(this); return g; }
    @Pure
    public int getH() { reads(this); return h; }

    public static void foo() {
        {
            // Default constructor
            Constructors c = new Constructors();
            check(c.getF()==3);
            check(c.getG()==22);
            check(c.getH()==4);
        }
        {
            // Constructor with only one argument
            Constructors c = new Constructors(10);
            check(c.getF()==10);
            check(c.getG()==22);
            check(c.getH()==33);
        }
        {
            // Constructor with only two arguments
            Constructors c = new Constructors(10, 15);
            check(c.getF()==10);
            check(c.getG()==15);
            check(c.getH()==33);
        }
        {
            // Constructor with three arguments
            Constructors c = new Constructors(10, 15, 20);
            check(c.getF()==10);
            check(c.getG()==15);
            check(c.getH()==20);
        }
        {
            // Errors
            Constructors c = new Constructors(10, 15, 20);
            check(c.getF()==11);
//          ^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
            check(c.getG()==16);
            check(c.getH()==21);
        }
    }
}