package com.aws.jverify.examples;

import com.aws.jverify.Reference;
import com.aws.jverify.Pure;

class ObjectRules {
    void mutableInheritsFromImmutableObject(Object immutableObject) {
        @Reference Object mutableObject = immutableObject;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type Object) not assignable to LHS (of type ModifiableObject)
        Object immutableObject2 = mutableObject; // upcast is legal
    }
    
    <T> void typeParameterExtendsImmutableObject(T t) {
        Object immutableObject = t; // upcast is legal
        @Reference Object mutableObject = t;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ModifiableObject)
    }
    
    void newObjectIsAlwaysModifiable() {
        @Reference Object o = new Object(); // no cast
    }

    @Pure
    @Reference
    Object newObjectIsImpure() {
        return new Object();
//             ^ error: using 'new' in an expression to create an instance of a mutable type is not supported
    }
}