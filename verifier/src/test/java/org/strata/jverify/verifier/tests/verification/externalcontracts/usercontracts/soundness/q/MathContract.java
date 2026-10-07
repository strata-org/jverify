package q;

import org.strata.jverify.Contract;
import org.strata.jverify.Pure;
import org.strata.jverify.Verify;

@Contract(Math.class)
class MathContract {
    public static int abs(int a) {
        return 7;
    }

    @Pure
    @Verify(false)
    public static int max(int a, int b) {
        return 7;
    }

    @Pure
    public static int min(int a, int b) {
        return 7;
    }
}
