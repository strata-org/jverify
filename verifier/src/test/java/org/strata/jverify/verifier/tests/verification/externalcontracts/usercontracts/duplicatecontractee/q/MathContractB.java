package q;

import org.strata.jverify.Contract;

    @Contract(Math.class)
//  ^ error: class 'Math' must not be referenced by more than one @Contract annotation
    class MathContractB {
    }
