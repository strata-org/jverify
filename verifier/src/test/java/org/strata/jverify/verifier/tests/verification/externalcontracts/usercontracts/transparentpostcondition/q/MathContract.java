package q;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;

import static org.strata.jverify.JVerify.*;

@Contract(Math.class)
class MathContract {
    @Transparent
    public static int max(int a, int b) {
        postcondition((int r) -> r >= a && r >= b);
//      ^ error: postconditions are not allowed in a transparent method
        return a > b ? a : b;
    }
}
