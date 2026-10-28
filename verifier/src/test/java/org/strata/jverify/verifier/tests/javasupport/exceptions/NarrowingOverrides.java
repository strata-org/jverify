package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * An override may declare fewer checked exceptions than the method it overrides, or
 * none, and an interface method keeps its {@code throws} clause. A call through the
 * base or the interface still has to handle the declared exception.
 */
@JVerifyTest
class NarrowingOverrides {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'

    static class Base {
        int m(int b) throws AppException {
            if (b == 0) {
                throw new AppException();
            }
            return b;
        }
    }

    static class Sub extends Base {
        @Override
        int m(int b) {
            return 1;
        }
    }

    interface Src {
        int read(int b) throws AppException;
    }

    static class Quiet implements Src {
        @Override
        public int read(int b) {
            return b;
        }
    }

    static class Loud implements Src {
        @Override
        public int read(int b) throws AppException {
            if (b == 0) {
                throw new AppException();
            }
            return b;
        }
    }

    static int viaBase(Base x, int b) throws AppException {
        int r = x.m(b);
        return r;
    }

    static int viaSrc(Src s, int b) throws AppException {
        int r = s.read(b);
        return r;
    }

    static int direct(Sub s, int b) {
        int r = s.m(b);
        return r;
    }
}
