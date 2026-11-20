package q;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;

@Contract(Math.class)
class MathSpec {
    @Transparent
    public static int negateExact(int a) {
        return 7;
    }
}
