package org.strata.jverify.cortex;

public interface ToIon {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);
}
