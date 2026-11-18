package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;
import static org.strata.jverify.JVerify.*;

// The contract of java.lang.Integer, used by both routes. A box is an object with one
// field that holds the primitive. valueOf allocates a new box, so the identity
// of a box is not Java's, which caches some; both routes refuse a reference
// comparison that may be between boxes.
@Contract(value = Integer.class, pure = true)
class IntegerContract {
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int MIN_VALUE = 0x80000000;

    private final int value;

    public IntegerContract(int value) {
        this.value = value;
    }

    @Transparent
    public static int sum(int a, int b) {
        precondition(!IntegerContractHelper.sumWillOverflow(a, b));
        return a + b;
    }

    public static IntegerContract valueOf(int i) {
        postcondition((IntegerContract boxed) -> boxed.intValue() == i);
        return new IntegerContract(i);
    }

    @Transparent
    public int intValue() {
        return value;
    }

    @Transparent
    public boolean equals(Object obj) {
        return obj instanceof IntegerContract ? value == ((IntegerContract) obj).value : false;
    }
}

class IntegerContractHelper {
    @Transparent
    public static boolean sumWillOverflow(int a, int b) {
        if (a > 0 && b > 0) {
            return a > Integer.MAX_VALUE - b;
        }
        if (a < 0 && b < 0) {
            return a < Integer.MIN_VALUE - b;
        }
        return false; // Different signs won't overflow
    }
}
