package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.5 -- Widening Reference Conversion: subtype to supertype / implemented interface
class WideningReferenceConversion_1 {
    static void run() {

        WrcDog_WideningReferenceConversion_1 dog = new WrcDog_WideningReferenceConversion_1();
        WrcAnimal_WideningReferenceConversion_1 animal = dog;           // any reference -> Object
        check(animal.legs() == 4);
    }
}

interface WrcNamed_WideningReferenceConversion_1 {
    int name();
}

class WrcAnimal_WideningReferenceConversion_1 {
    int legs() { return 4; }
}

class WrcDog_WideningReferenceConversion_1 extends WrcAnimal_WideningReferenceConversion_1 implements WrcNamed_WideningReferenceConversion_1 {
    public int name() { return 7; }
}
