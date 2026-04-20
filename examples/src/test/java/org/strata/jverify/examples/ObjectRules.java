package org.strata.jverify.examples;

import org.strata.jverify.Impure;
import org.strata.jverify.Pure;

@SuppressWarnings("unused")
class ObjectRules {
    void impureInheritsFromPureObject(Object pureObject1) {
        @Impure Object impureObject = pureObject1;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type Object) not assignable to LHS (of type ImpureObject)
        Object pureObject2 = impureObject; // upcast is legal
    }
    
    @Pure
    @Impure Object newObjectIsImpure() {
        return new Object(); // no cast needed
//             ^ error: using 'new' in a pure expression to create an instance of an impure type is not allowed
    }
    
    <T> void typeParameterExtendsPureObject(T t) {
        Object pureObject = t; // upcast is legal
        @Impure Object impureObject = t;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ImpureObject)
    }
}