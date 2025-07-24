package com.aws.jverify.examples;

import com.aws.jverify.Nullable;
import static com.aws.jverify.JVerify.modifies;

public class NullCheck {    
    @Nullable
    Fooer fooer;
    
    void useCanBeNull() {
        modifies(this);
        fooer.foo();
//      ^^^^^ Error: target object might be null
        if (fooer != null) {
            fooer.foo();
            bar();
            fooer.foo();
//          ^^^^^ Error: target object might be null
        }    
    }
    
    void bar() {
        modifies(this);
        fooer = null;
    }
    
}

class Fooer {
    void foo() {}
}
