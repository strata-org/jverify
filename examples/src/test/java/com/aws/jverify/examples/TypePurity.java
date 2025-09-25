package com.aws.jverify.examples;

import com.aws.jverify.Contract;
import com.aws.jverify.Impure;
import com.aws.jverify.Pure;

import static com.aws.jverify.JVerify.*;

interface PureInterface {
    void increment();
    int getX();
    
    @Contract
    class MyContract implements PureInterface {
        int x;

        public void increment() {
            modifies(this); // error
//                   ^^^^ Error: a modifies-clause expression must denote an object, a single field location like o`x or a`[i] of type (object, field)  (with `--referrers`), or a set/iset/multiset/seq of objects or single field locations (with `--referrers`) (instead got PureInterface)
            postcondition(x == old(x) + 1);
        }
        
        @Pure
        public int getX() {
            return x;
        }
    }   
}

@Impure
interface ImpureInterface {
    void increment();
    int getX();

    @Contract
    class MyContract implements ImpureInterface {
        int x;

        public void increment() {
            modifies(this);
            postcondition(x == old(x) + 1);
        }

        @Pure
        public int getX() { return x; }
    }
}

   record RecordsArePure(int x) 
// ^ error: a record class may not be annotated with @Impure, or extend or implement a type annotated with @Impure
        implements ImpureInterface
{
    @Pure
    static RecordsArePure createR() {
        return new RecordsArePure(3); // legal
    }
    
    @Pure
    public boolean compare(RecordsArePure other) {
        return this == other;
//                  ^ error: '==' is only allowed when at least one operand's type is mutable
    }

    @Override
    public void increment() {
        modifies(this);
//               ^^^^ Error: a modifies-clause expression must denote an object, a single field location like o`x or a`[i] of type (object, field)  (with `--referrers`), or a set/iset/multiset/seq of objects or single field locations (with `--referrers`) (instead got RecordsArePure)
    }

    @Override
    public int getX() {
        return x;
    }
}

class ClassesAreImpure {
    @Pure
    ClassesAreImpure createC() {
        return new ClassesAreImpure();
//             ^ error: using 'new' in a pure expression to create an instance of an impure type is not supported
    }
}