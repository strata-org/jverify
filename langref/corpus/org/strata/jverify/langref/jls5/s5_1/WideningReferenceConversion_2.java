package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.5 -- Widening Reference Conversion: subtype to supertype / implemented interface
class WideningReferenceConversion_2 {
    static void run() {

        WrcDog_WideningReferenceConversion_2 dog = new WrcDog_WideningReferenceConversion_2();     // subclass -> superclass
        WrcNamed_WideningReferenceConversion_2 named = dog;
        check(named.name() == 7);
    }
}

interface WrcNamed_WideningReferenceConversion_2 {
    int name();
}

class WrcAnimal_WideningReferenceConversion_2 {
    int legs() { return 4; }
}

class WrcDog_WideningReferenceConversion_2 extends WrcAnimal_WideningReferenceConversion_2 implements WrcNamed_WideningReferenceConversion_2 {
    public int name() { return 7; }
}
