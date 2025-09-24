package com.aws.jverify.examples;

import com.aws.jverify.Pure;
import com.aws.jverify.PureRef;

class ObjectRules {
    void mutableInheritsFromImmutableObject(Object immutableObject) {
        Object mutableObject = immutableObject;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type Object) not assignable to LHS (of type ModifiableObject)
        @PureRef Object immutableObject2 = mutableObject; // upcast is legal
    }
    
    <@PureRef T> void typeParameterExtendsImmutableObject(T t) {
        @PureRef Object immutableObject = t; // upcast is legal
        Object mutableObject = t;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ModifiableObject)
    }
    
    void newObjectIsAlwaysModifiable() {
        Object o = new Object(); // no cast
    }

    @Pure
    Object newObjectIsImpure() {
        return new Object();
//             ^ error: using 'new' in a pure expression to create an instance of a mutable type is not supported
    }
}