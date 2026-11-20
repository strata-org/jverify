package p;

// Definite assignment (JLS 16) that relies on a constant condition or a constant operand of a conditional
// operator. Each method compiles with plain javac; the uses of x are legal only because javac knows the
// constant's value.
public class DA {
    static final int A = 1;
    static boolean flag = Boolean.parseBoolean(System.getProperty("da.flag", "true"));

    // || with a constant-false left operand: x is assigned before every reachable use
    static int t1() { int x; if ((A < 0) || (x = 1) > 0) return x; return -1; }

    // && with a constant-true left operand, x read in the else branch
    static int t2() { int x; if ((A > 0) && (x = 2) < 0) return -1; else return x; }

    // && with a constant-false left operand: the then branch is vacuously reachable, x never assigned
    static int t3() { int x; if ((A < 0) && flag) return x; return 3; }

    // value context: x definitely assigned after the expression, through the constant
    static int t4() { int x; boolean r = (A > 0) && ((x = 4) > 0); return x; }

    // ?: in a value context: the true branch reads x vacuously
    static int t5() { int x; int y = ((A < 0) && flag) ? x : 5; return y; }

    // while condition: x used after the loop
    static int t6() { int x; while ((A > 0) || flag) { x = 6; break; } return x; }

    // a constant LOCAL as the operand
    static int t7() { final boolean t = true; int x; if (t && (x = 7) > 0) return x; else return x; }

    // !
    static int t8() { int x; if (!(A > 0) || (x = 8) > 0) return x; return -1; }

    // boolean ?: with a constant branch
    static int t9() { int x; if (flag ? (x = 9) > 0 : (A < 0)) return x; return -1; }

    // boolean switch expression as a condition, with a constant yield
    static int t10(int k) {
        int x;
        if (switch (k) { case 1 -> (A < 0); default -> (x = 10) > 0; }) return x;
        return -1;
    }

    // value context, no blank local: stays unfoldable (javap: a compare, not iconst_1)
    static boolean t11() { return (A > 0) && !(A < 0); }

    // a case guard: x is read in the body only if the guard holds
    static int t12(Object o) {
        int x;
        switch (o) {
            case Integer i when (A < 0) && flag -> { return x; }
            default -> { return 12; }
        }
    }

    // an assert's detail is evaluated only if its condition is false
    static int t13() { int x; assert (A > 0) || flag : x; return 13; }

    // an if that does not mention x: x is assigned after it only because A > 0 is true and its body returns
    static int t14() { int x; if (A > 0) return 14; return x; }

    // a local declared in an if's condition, read there through a constant
    static int t15(int k) {
        if (switch (k) { default -> { int y; yield ((A < 0) && flag) ? y == 1 : true; } }) return 15;
        return -1;
    }

    // as t14, with a local class between x and the if
    static int t16() {
        int x;
        class L {
            int g() { return 16; }
        }
        if (A > 0) return new L().g();
        return x;
    }

    // a do-while (true): x is assigned after it only because the loop is left by the break alone
    static int t17() {
        int x;
        do {
            if (flag) {
                x = 17;
                break;
            }
        } while (true);
        return x;
    }

    public static void main(String[] args) {
        System.out.println(t1() + " " + t2() + " " + t3() + " " + t4() + " " + t5() + " " + t6() + " "
                + t7() + " " + t8() + " " + t9() + " " + t10(2) + " " + t11() + " " + t12(5) + " " + t13() + " "
                + t14() + " " + t15(2) + " " + t16() + " " + t17());
    }
}
