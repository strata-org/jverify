package q;

import org.strata.jverify.Contract;
import org.strata.jverify.Pure;

@Contract(Boolean.class)
class BooleanContract {
    @Pure
    public static boolean logicalAnd(boolean a, boolean b) {
        return true;
    }
}
