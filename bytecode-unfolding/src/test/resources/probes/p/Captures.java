package p;

import java.util.function.IntSupplier;

// Constant locals that local and anonymous classes read, and blank locals in field initializers, where
// definite assignment relies on a constant condition.
public class Captures {
    static final int A = 3;

    static int fromStaticSwitch = switch (0) {
        default -> {
            int x;
            boolean b = A < 0 || (x = 1) > 0;
            yield x;
        }
    };

    int fromInstanceSwitch = switch (1) {
        default -> {
            int x;
            boolean b = A < 0 || (x = 2) > 0;
            yield x;
        }
    };

    static int localClass() {
        final boolean t = true;
        class L {
            int f() {
                int x;
                if (t) x = 3;
                return x;
            }
        }
        return new L().f();
    }

    static int anonymousClass() {
        final boolean t = true;
        IntSupplier s = new IntSupplier() {
            @Override
            public int getAsInt() {
                int x;
                if (t) x = 4;
                return x;
            }
        };
        return s.getAsInt();
    }

    static int nestedClasses() {
        final boolean t = true;
        class Outer {
            int f() {
                class Inner {
                    int g() {
                        int x;
                        if (!t) return 0;
                        x = 5;
                        return t ? x : 0;
                    }
                }
                return new Inner().g();
            }
        }
        return new Outer().f();
    }

    static int capturedArithmetic() {
        final int k = 7;
        class L {
            int f() {
                return k / 2 + (byte) 300;
            }
        }
        return new L().f() + k * 2;
    }

    public static void main(String[] args) {
        System.out.println(fromStaticSwitch + " " + new Captures().fromInstanceSwitch + " " + localClass() + " "
                + anonymousClass() + " " + nestedClasses() + " " + capturedArithmetic());
    }
}
