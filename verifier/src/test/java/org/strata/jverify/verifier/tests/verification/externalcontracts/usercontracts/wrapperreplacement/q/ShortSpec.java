package q;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;

@Contract(pure = true, value = Short.class)
class ShortSpec {
    @Transparent
    public static int toUnsignedInt(short s) {
        return 7;
    }
}
