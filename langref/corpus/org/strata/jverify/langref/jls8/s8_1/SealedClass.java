package org.strata.jverify.langref.jls8.s8_1;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.1.2 -- sealed/non-sealed/final Classes: a sealed class restricts its
// direct subclasses; final and non-sealed subclasses complete the hierarchy.
class SealedClass {
    static void run() {
        SealedBase a = new FinalLeaf();
        SealedBase b = new OpenLeaf();
        check(a.tag() + b.tag() == 3);
    }
}

sealed abstract class SealedBase permits FinalLeaf, OpenLeaf {
    abstract int tag();
}

final class FinalLeaf extends SealedBase {
    @Override
    int tag() {
        return 1;
    }
}

non-sealed class OpenLeaf extends SealedBase {
    @Override
    int tag() {
        return 2;
    }
}
