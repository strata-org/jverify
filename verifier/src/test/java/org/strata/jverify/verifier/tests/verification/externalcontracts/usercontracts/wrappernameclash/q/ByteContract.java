package q;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;

    @Contract(Math.class)
    class ByteContract {
//  ^ error: contract classes 'org.strata.jverify.builtin.ByteContract' and 'q.ByteContract' share the simple name 'ByteContract'; contract class simple names must be unique
        @Transparent
        public static int negateExact(int a) {
            return 7;
        }
    }
