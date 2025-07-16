package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, dafnyVerified = 9, dafnyErrors = 1, resolvePrintedDafny = true)
class Overloads {
    
    private int f;
    private int g = 22;
    private int h = 33;
    public Overloads() {
        postcondition(this.g==22); // Value of g is set by the default initializer
        postcondition(this.f==3);  // Value of f is set by the constructor
        postcondition(this.h==4);  // Value of h is set by the default initializer then changed by the constructor
        f = 3;
        h = 4;
    }

    public Overloads(int f_) {
        postcondition(this.f==f_ && this.g==22 && this.h==33);
        f = f_;
    }

    public Overloads(int f_, int g_) {
        postcondition(this.f==f_ && this.g==g_ && this.h==33);
        f = f_;
        g = g_;
    }

    public Overloads(int f_, int g_, int h_) {
        postcondition(this.f==f_ && this.g==g_ && this.h==h_);
        f = f_;
        g = g_;
        h = h_;
    }

    public Overloads(Overloads other) {
        this.f = other.f;
        this.g = other.g;
        this.h = other.h;
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
            Overloads c = new Overloads();
            check(c.f()==3);
            check(c.g()==22);
            check(c.h()==4);
        }
        {
            // Constructor with only one argument
            Overloads c = new Overloads(10);
            check(c.f()==10);
            check(c.g()==22);
            check(c.h()==33);
        }
        {
            // Constructor with only two arguments
            Overloads c = new Overloads(10, 15);
            check(c.f()==10);
            check(c.g()==15);
            check(c.h()==33);
        }
        {
            // Constructor with three arguments
            Overloads c = new Overloads(10, 15, 20);
            check(c.f()==10);
            check(c.g()==15);
            check(c.h()==20);
        }
        {
            Overloads c = new Overloads(10, 15, 20);
            int tmp = c.h();
            int tmp2 = c.h(c.h());
            check(tmp2 == tmp+1);
        }
        {
            // Errors
            Overloads c = new Overloads(10, 15, 20);
            check(c.f()==11);
//          ^^^^^^^^^^^^^^^^ Error: assertion might not hold
            check(c.g()==16);
            check(c.h()==21);
        }
        {
            Overloads c = new Overloads(10, 15, 20);
            Overloads c2 = new Overloads(c);
            check(c2.f()==10);
        }
    }
    void overloads(List<? extends Object> elements) {}
    void overloads(int x) {}
}