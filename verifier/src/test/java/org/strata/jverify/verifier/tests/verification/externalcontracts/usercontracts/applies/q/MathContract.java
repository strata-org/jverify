package q;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;

import static org.strata.jverify.JVerify.*;

@Contract(Math.class)
class MathContract {
    public static int abs(int a) {
        precondition(a > Integer.MIN_VALUE);
        postcondition((int r) -> r >= 0 && (r == a || r == -a));
        throw new ContractException();
    }
}
