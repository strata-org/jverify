package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

class Foo {
//    ^ error: class 'Foo' may not be verified because it has an externally defined contract 
    void bar(int x) {}
}

@Contract(Foo.class)
class FooContract1 {    
    void bar(int x) {
        check(x > 0);
    }
}

  @Contract(Foo.class)
//^ error: a class may only be referenced by a @Contract annotation once
class FooContract2 {
    void bar(int x) {
        check(x < 0);
    }
    
    @Contract(Foo.class)
//  ^ error: a class with @Contract may not be nested inside another type
    class NestedContractClass {
    }
}