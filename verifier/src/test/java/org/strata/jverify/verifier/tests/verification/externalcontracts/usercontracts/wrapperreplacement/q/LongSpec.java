package q;

import org.strata.jverify.Transparent;

// A contract commented out describes nothing, so Integer keeps its shipped contract:
// @Contract(Integer.class)
@org.strata.jverify.Contract(Long.class)
class LongSpec {
    @Transparent
    public static long max(long a, long b) {
        return a;
    }
}
