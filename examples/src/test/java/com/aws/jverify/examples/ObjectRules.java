package com.aws.jverify.examples;

import com.aws.jverify.Reference;
import com.aws.jverify.Pure;

class ObjectRules {
    void referenceObjectInheritsFromObject(Object object) {
        @Reference Object referenceObject = object;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type Object) not assignable to LHS (of type ReferenceObject)
        Object regularObject = referenceObject; // upcast is legal
    }
    
    <T> void typeParameterExtendsImmutableObject(T t) {
        Object object = t; // upcast is legal
        @Reference Object referenceObject = t;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ReferenceObject)
    }
    
    void newObjectIsAlwaysReference() {
        @Reference Object o = new Object(); // no cast
    }

    @Pure
    @Reference
    Object newObjectIsImpure() {
        return new Object();
//             ^ error: using 'new' in an expression to create an instance of a mutable type is not supported
    }
}