package org.strata.jverify.langref.jls4.s4_5;

import static org.strata.jverify.JVerify.*;

// JLS 4.5.2 -- Members and Constructors of Parameterized Types: a member field, a
// member method, and a constructor whose signatures are obtained by substituting the
// type arguments for the type parameters of the generic class.
class MembersAndConstructorsOfParameterizedTypes_2 {
    static void run() {

        // Constructor Pair(Integer, Integer) -- substituted from Pair(A, B).
        Pair<Integer, Integer> p = new Pair<Integer, Integer>(3, 4);
        // Member methods first()/second() have substituted return types Integer.
        int sum = p.first() + p.second();

        // Member method with substituted parameter type.
        Pair<Integer, Integer> p2 = p.withFirst(10);
        check(p2.first() == 10);
    }

    static class Pair<A, B> {
        final A a;   // member field with substituted type A
        final B b;   // member field with substituted type B
        Pair(A a, B b) { this.a = a; this.b = b; }
        A first() { return a; }
        B second() { return b; }
        Pair<A, B> withFirst(A newA) { return new Pair<A, B>(newA, b); }
    }
}
