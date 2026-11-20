package q;

import org.strata.jverify.Contract;
import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;

import java.util.concurrent.atomic.AtomicInteger;

import static org.strata.jverify.JVerify.*;

@Contract(AtomicInteger.class)
class AtomicIntegerContract {
    int value;

    @Verify(false)
    public AtomicIntegerContract(int initialValue) {
        postcondition(value == initialValue);
        this.value = 0;
    }

    @Transparent
    public int get() {
        reads(this);
        return value;
    }
}
