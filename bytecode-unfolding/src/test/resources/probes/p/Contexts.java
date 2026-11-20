package p;

import java.util.function.IntSupplier;

// Constant operations in contexts with no enclosing method: lambdas in field initializers, initializer
// blocks, enum constant arguments, interface fields.
public class Contexts {
    static IntSupplier s = () -> (byte) 300 + 7 / 2;
    IntSupplier i = () -> (char) 65 + -(1 << 3);
    static int fromStaticBlock;
    int fromInstanceBlock;

    static {
        int k = (short) 70000;
        fromStaticBlock = k + (int) 3.9e0 % 2;
    }

    {
        long j = (long) -0.5 + (1L << 40) / 3;
        fromInstanceBlock = (int) j;
    }

    enum E {
        X(1 + 2), Y((byte) 200), Z((int) Double.NaN - 1);

        final int v;

        E(int v) {
            this.v = v;
        }
    }

    interface Q {
        int N = Integer.parseInt("5") + 7 / 2;
        IntSupplier R = () -> (int) 2.5 * 3;
    }

    record R(int a) {
        R {
            a = a + (byte) 129;
        }
    }

    public static void main(String[] args) {
        Contexts c = new Contexts();
        System.out.println(s.getAsInt() + " " + c.i.getAsInt() + " " + fromStaticBlock + " " + c.fromInstanceBlock
                + " " + E.X.v + " " + E.Y.v + " " + E.Z.v + " " + Q.N + " " + Q.R.getAsInt() + " " + new R(1).a());
    }
}
