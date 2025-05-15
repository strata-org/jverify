// TEST: exitCode=0 dafnyVerified=4 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
//import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;

//@JVerifyTest
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
        g = g_;
    }

    public Constructors(int f_, int g_, int h_) {
        postcondition(this.f==f_ && this.g==g_ && this.h==h_);
        f = f_;
        g = g_;
        h = h_;
    }

    @Pure
    public int f() { reads(this); return f; }
    @Pure
    public int g() { reads(this); return g; }
    @Pure
    public int h() { reads(this); return h; }
    @Pure
    public int h(int x) {precondition(x<=100);return x+1;}

    public static void foo() {
        {
            // Default constructor
            Constructors c = new Constructors();
            check(c.f()==3);
            check(c.g()==22);
            check(c.h()==4);
        }
        {
            // Constructor with only one argument
            Constructors c = new Constructors(10);
            check(c.f()==10);
            check(c.g()==22);
            check(c.h()==33);
        }
        {
            // Constructor with only two arguments
            Constructors c = new Constructors(10, 15);
            check(c.f()==10);
            check(c.g()==15);
            check(c.h()==33);
        }
        {
            // Constructor with three arguments
            Constructors c = new Constructors(10, 15, 20);
            check(c.f()==10);
            check(c.g()==15);
            check(c.h()==20);
        }
        {
            Constructors c = new Constructors(10, 15, 20);
            int tmp = c.h();
            int tmp2 = c.h(c.h());
            check(tmp2 == tmp+1);
        }
        {
            // Errors
            Constructors c = new Constructors(10, 15, 20);
            check(c.f()==11);
//          ^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
            check(c.g()==16);
            check(c.h()==21);
        }
    }
}