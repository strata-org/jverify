package org.strata.jverify.laurel;

public interface ToIon {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);
}
