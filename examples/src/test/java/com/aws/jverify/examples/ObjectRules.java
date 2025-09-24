package com.aws.jverify.examples;

import com.aws.jverify.Impure;
import com.aws.jverify.Pure;

class ObjectRules {
    void impureInheritsFromPureObject(Object pureObject) {
        @Impure Object impureObject = pureObject;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type Object) not assignable to LHS (of type ImpureObject)
        Object pureObject2 = impureObject; // upcast is legal
    }
    
    <T> void typeParameterExtendsImmutableObject(T t) {
        Object pureObject = t; // upcast is legal
        @Impure Object impureObject = t;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ImpureObject)
    }
    
    void newObjectIsAlwaysImpure() {
        @Impure Object o = new Object(); // no cast
    }

    @Pure
    @Impure
    Object newObjectIsImpure() {
        return new Object();
//             ^ error: using 'new' in a pure expression to create an instance of a mutable type is not supported
    }
}