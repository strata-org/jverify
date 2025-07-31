package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.MethodContract;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(dafnyVerified = 8, dafnyErrors = 0)
public class EqualsAndHashCode {

}

class MyPair<A, B> {

    private final A a;
    private final B b;

    MyPair(A a, B b) {
        this.a = a;
        this.b = b;
    }

    @Pure
    @MethodContract("equals")
    public boolean equalsContract(Object obj) {
        MyPair<A, B> other;
        return (obj == null || !getClass().equals(obj.getClass()))
            ? false
            : ((other = (MyPair<A, B>)obj) != null && a.equals(other.a) && b.equals(other.b));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        var other = (MyPair)obj;
        return a.equals(other.a) && b.equals(other.b);
    }
}

class A {

    int f;

    public A(int f) {
        this.f = f;
    }

    @Override
    @Pure
    public boolean equals(Object obj) {
        if (!(obj instanceof A)) {
            return false;
        }

        A other = (A) obj;
        return f == other.f;
    }
}

class B extends A {

    int g;

    public B(int f, int g) {
        super(f);
        this.g = g;
    }

    @Override
    @Pure
    public boolean equals(Object obj) {
        if (!(obj instanceof B)) {
            return false;
        }

        B other = (B) obj;
        return f == other.f && g == other.g;
    }
}

class WhyItDoesNotWork {

    public void OhNo() {
        var a = new A(1);
        var b = new B(1, 2);

        check(a.equals(b)); // true
        check(b.equals(a)); // false
        // Oops, equals is not symmetrical ==> not an equivalence relation
    }

}