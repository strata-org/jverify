package q;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.testing.ThrowingWriter;

import static org.strata.jverify.JVerify.*;

@Contract(ThrowingWriter.class)
class ThrowingWriterContract {
    public static void writeThenThrow(Object o) throws Exception {
        throwsOn(true).modifies(o);
        throw new ContractException();
    }
}
