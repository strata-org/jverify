package p2;

import org.strata.jverify.Contract;

    @Contract(StrictMath.class)
    class Dup {
//  ^ error: contract classes 'p1.Dup' and 'p2.Dup' share the simple name 'Dup'; contract class simple names must be unique
    }
