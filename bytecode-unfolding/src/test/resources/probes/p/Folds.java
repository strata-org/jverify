package p;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Constant folding in every context constant unfolding touches or must leave alone. Prints one line per probe. */
public class Folds {
    static final int A = 10;
    static final int B = A * 3 + 2;
    static final String S = "ab";
    static final long BIG = 1L << 40;
    static int nonFinalField = 7 / 2 + (int) 3.9;
    int instanceField = (byte) 300 + 'a';
    static final boolean DEBUG = false;

    interface K { int X = 6 * 7; }
    enum Color { RED, GREEN }
    @interface Ann { int value(); }

    static void out(String name, Object v) { System.out.println(name + "=" + v); }

    static int sideEffects;

    static boolean sideEffect() { sideEffects++; return true; }

    @Ann(A * 2)
    static int annotated() { return A; }

    public static void main(String[] args) {
        // arithmetic, bitwise, shifts, comparisons
        out("div", 7 / 2);
        out("mod", -7 % 3);
        out("ovf", 100000 * 100000);
        out("lovf", Long.MAX_VALUE + 1);
        out("shl", 1 << 33);
        out("ushr", -1 >>> 28);
        out("xor", 0b1010 ^ 0b0110);
        out("cmp", 3 < 4 == true);
        out("neg", -Integer.MIN_VALUE);
        out("lneg", -Long.MIN_VALUE);
        out("dneg", -0.0);
        out("chr", (char) ('a' + 1));
        out("bool", true && !false || false);
        // a constant operand beside a call: the call still runs only when the operator needs it
        out("shortCircuit", (false && sideEffect()) + "," + (true || sideEffect()) + "," + (true && sideEffect())
                + "," + (true ? 1 : (sideEffect() ? 2 : 3)) + "," + sideEffects);
        // casts of constants, incl. ones javac's code generator folds itself
        out("nan", (int) Double.NaN);
        out("inf", (long) Double.POSITIVE_INFINITY);
        out("narrow", (byte) 300);
        out("i2c", (char) 65601);
        out("f2i", (int) 3.99f);
        out("d2f", (float) 1e40);
        // compile-time narrowing in assignment context
        byte b = 10 + 20;
        short sh = 'a' + 1;
        char c = 65 + 1;
        out("narrowAssign", b + "," + sh + "," + c);
        // constant variables: locals and fields
        final int L = 5;
        final long LL = L * 2L;
        var v = L + 1;
        out("locals", L + LL + v);
        out("fields", A + B + K.X + BIG);
        out("nonFinalField", nonFinalField);
        out("instanceField", new Folds().instanceField);
        // Strings: constants must stay interned
        final String t = "a";
        out("intern1", (t + "b") == S);
        out("intern2", ("a" + "b") == "ab");
        out("concat", "x" + 1 + 2 + 'c' + 1.5);
        // ternaries
        out("tern1", true ? 1 : 2);
        out("tern2", L > 3 ? "big" : "small");
        out("tern3", (A > 5 ? (byte) 1 : 'x'));
        // constant conditions: reachability and definite assignment were decided on them
        int da;
        if (true) { da = 1; }
        out("da", da);
        final boolean F = false;
        int db;
        if (!F) { db = 2; } else { db = 3; }
        out("db", db);
        if (DEBUG) { out("debug", "unreachable"); }
        int loops = 0;
        while (true) { if (++loops > 2) break; }
        out("loops", loops);
        // switch labels must stay constant
        switch (args.length + 2) {
            case L - 3: out("switch", "two"); break;
            case A: out("switch", "ten"); break;
            default: out("switch", "other");
        }
        switch ("ab") {
            case S: out("sswitch", "S"); break;
            default: out("sswitch", "other");
        }
        out("yield", switch (L) { case 5 -> L * 2; default -> -1; });
        out("enum", switch (Color.GREEN) { case RED -> 1; case GREEN -> 2; });
        // lambdas, local and anonymous classes capturing constant locals
        IntSupplier lam = () -> L * 3 + (int) 2.5 + (byte) 257;
        out("lambda", lam.getAsInt());
        Supplier<Integer> nested = () -> { IntSupplier in = () -> (int) Double.NaN + L; return in.getAsInt(); };
        out("nested", nested.get());
        class Local { int f() { return L + A + (char) 66; } }
        out("local", new Local().f());
        Object anon = new Object() { @Override public String toString() { return "" + (L * L) + (short) 70000; } };
        out("anon", anon);
        IntSupplier sw = () -> switch (L) { case 5 -> 50; default -> 0; };
        out("lambdaSwitch", sw.getAsInt());
        // annotation values and assert with a constant condition (assertions off)
        out("ann", annotated());
        assert 1 > 2 : "never evaluated with -da";
        out("assert", "ok");
        // labeled break and nested constants
        outer:
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i * j == 2 * 1) break outer;
            }
        }
        out("labeled", "ok");
    }
}
