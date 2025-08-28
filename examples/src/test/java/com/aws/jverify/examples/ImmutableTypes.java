package com.aws.jverify.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.Modifiable;
import com.aws.jverify.Pure;

import static com.aws.jverify.JVerify.*;

interface Immutable {
    void increment();
    int getX();
    
    @Contract
    class MyContract implements Immutable {
        int xr;

        public void increment() {
            modifies(this); // error
//                   ^^^^ Error: a modifies-clause expression must denote an object, a single field location like o`x or a`[i] of type (object, field)  (with `--referrers`), or a set/iset/multiset/seq of objects or single field locations (with `--referrers`) (instead got Immutable)
            postcondition(xr == old(xr) + 1);
        }
        
        @Pure
        public int getX() {
            return xr;
        }
    }   
}

@Modifiable
interface Mutable {
    void increment();
    int getX();

    @Contract
    class MyContract implements Mutable {
        int x;

        public void increment() {
            modifies(this);
            postcondition(x == old(x) + 1);
        }

        @Pure
        public int getX() { return x; }
    }
}

   record RecordsAreImmutable(int x) 
// ^ error: a record class may not be annotated with @Modifiable, or extend or implement a type annotated with @Modifiable
        implements Mutable
{
    @Pure
    static RecordsAreImmutable createR() {
        return new RecordsAreImmutable(3); // legal
    }
    
    @Pure
    public boolean compare(RecordsAreImmutable other) {
        return this == other;
//                  ^ error: '==' is only allowed when at least one operand's type is mutable
    }

    @Override
    public void increment() {
        modifies(this);
//               ^^^^ Error: a modifies-clause expression must denote an object, a single field location like o`x or a`[i] of type (object, field)  (with `--referrers`), or a set/iset/multiset/seq of objects or single field locations (with `--referrers`) (instead got RecordsAreImmutable)
    }

    @Override
    public int getX() {
        return x;
    }
}

class ClassesAreMutable {
    @Pure
    ClassesAreMutable createC() {
        return new ClassesAreMutable();
//             ^ error: using 'new' in an expression to create an instance of a mutable type is not supported
    }
}