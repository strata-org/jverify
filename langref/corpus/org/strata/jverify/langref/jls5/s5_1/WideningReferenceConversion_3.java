package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.5 -- Widening Reference Conversion: subtype to supertype / implemented interface
class WideningReferenceConversion_3 {
    static void run() {

        WrcDog_WideningReferenceConversion_3 dog = new WrcDog_WideningReferenceConversion_3();       // class -> implemented interface
        Object obj = dog;
        check(obj != null);
    }
}

interface WrcNamed_WideningReferenceConversion_3 {
    int name();
}

class WrcAnimal_WideningReferenceConversion_3 {
    int legs() { return 4; }
}

class WrcDog_WideningReferenceConversion_3 extends WrcAnimal_WideningReferenceConversion_3 implements WrcNamed_WideningReferenceConversion_3 {
    public int name() { return 7; }
}
