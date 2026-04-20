package org.strata.jverify.examples;

import org.strata.jverify.Nullable;
import static org.strata.jverify.JVerify.modifies;

/**
 * Unlike most existing null-checking tools that rely solely on nullness annotations,
 * JVerify can detect all null reference exceptions without emitting false warnings.
 */
public class SimpleNullCheck {    
    @Nullable
    Fooer fooer;
    
    void useFooer() {
        modifies(this);
        fooer.foo();
//      ^^^^^ Error: target object could not be proved to be non-null
        if (fooer != null) {
            fooer.foo();
            clear();
            fooer.foo();
//          ^^^^^ Error: target object could not be proved to be non-null
        }    
    }
    
    void clear() {
        modifies(this);
        fooer = null;
    }
    
}

class Fooer {
    void foo() {}
}
