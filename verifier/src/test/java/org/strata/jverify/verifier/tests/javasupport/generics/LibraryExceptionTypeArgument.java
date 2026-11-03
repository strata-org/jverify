package org.strata.jverify.verifier.tests.javasupport.generics;

import java.io.IOException;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A library exception reached through a generic signature is the same type as everywhere else: a
 * generic method's parameter or result (with code or without), a field's, a local's (declared at
 * the store or hoisted) and a supertype's instantiation, a constructor's inferred one, a wildcard's
 * bound, and a class nested in a parameterised one.
 */
@JVerifyTest(methodsVerified = 18, methodsSkipped = 1, errorCount = 0)
class LibraryExceptionTypeArgument {
    static class Holder<T> {
        T val;
    }

    static class Outer<T> {
        class Inner<U> {
            U val;
        }
    }

    static class Box<T> {
        T val;

        // skipped, as is every generic class's constructor
        Box(T v) {
            val = v;
        }
    }

    static class IOHolder extends Holder<IOException> { }

    interface Source {
        <T> IOException pick(IOException e, T t);
    }

    Holder<IOException> field;

    static <T> IOException first(IOException e, T t) {
        return e;
    }

    static IOException passes(IOException e) {
        return first(e, e);
    }

    static void storeLocal(IOException e) {
        Holder<IOException> h = new Holder<>();
        h.val = e;
    }

    static void storeHoisted(boolean b, IOException e) {
        Holder<IOException> h;
        if (b) {
            h = new Holder<>();
        } else {
            h = new Holder<>();
        }
        h.val = e;
    }

    static Box<IOException> boxed(IOException e) {
        return new Box<>(e);
    }

    void storeField(IOException e) {
        field.val = e;
    }

    static void storeSubclass(IOHolder h, IOException e) {
        h.val = e;
    }

    static IOException viaInterface(Source s, IOException e) {
        return s.pick(e, e);
    }

    static void takeSuper(Holder<? super IOException> h) {
    }

    static void callSuper(Holder<Object> h) {
        takeSuper(h);
    }

    static void takeExtends(Holder<? extends IOException> h) {
    }

    static void callExtends(Holder<IOException> h) {
        takeExtends(h);
    }

    static void storeNested(Outer<String>.Inner<IOException> h, IOException e) {
        h.val = e;
    }
}
