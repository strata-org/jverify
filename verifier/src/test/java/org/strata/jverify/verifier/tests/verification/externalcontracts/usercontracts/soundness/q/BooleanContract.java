package q;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;

@Contract(Boolean.class)
class BooleanContract {
    @Transparent
    public static boolean logicalAnd(boolean a, boolean b) {
        return true;
    }
}
