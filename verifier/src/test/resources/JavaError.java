package org.strata.jverify;

class JavaError {
    int foo() {
        return true;
//             ^ error: Java compilation had an error: incompatible types: boolean cannot be converted to int        
    }
}
