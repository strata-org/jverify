package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.JVerify;
import com.aws.jverify.MethodContract;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;
import java.util.Objects;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.forall;
import static com.aws.jverify.JVerify.implies;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;
import static com.aws.jverify.JVerify.returns;

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

interface ImmutableList<T> {
    T get(int index);

    @Unbounded int size();

    boolean equals(Object obj);

    @MethodContract("equals")
    static <T> boolean equalsContract(ImmutableList<T> thiz, Object obj) {
        returns((obj instanceof ImmutableList<?> other)
                && (thiz.size() == other.size())
                && forall((int i) ->
                implies(0 <= i && i < thiz.size(),
                        Objects.equals(thiz.get(i), other.get(i)))));
        throw new ContractException();
    }
}

class SingletonList<T> implements ImmutableList<T> {

    final T value;

    public SingletonList(T value) {
        this.value = value;
    }

    public T get(int index) {
        if (index == 0) {
            return value;
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    public @Unbounded int size() {
        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SingletonList<?>)) {
            return false;
        }
        var other = (ImmutableList<?>) obj;
        return other.size() == 1 && value.equals(other.get(1));
    }
}