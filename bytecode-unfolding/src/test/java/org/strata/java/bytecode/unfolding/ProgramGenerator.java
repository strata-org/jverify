package org.strata.java.bytecode.unfolding;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Random programs for checking that constant unfolding keeps a program's meaning: constant expressions, and
 * some partly constant ones, of every primitive type, in every context the rewrite treats differently.
 * They include definite-assignment shapes that are legal only through a constant boolean of known value
 * (JLS 16). Each member returns a String, and main prints them all.
 */
final class ProgramGenerator {
    private static final String NUM = "BSCIJFD";
    private static final String NAMES = "BSCIJFDZ";
    private static final String[][] CONST_FIELDS = {
        {"I", "CI0", "7"}, {"I", "CI1", "-3"}, {"I", "CI2", "2147483647"}, {"J", "CJ0", "9000000000L"},
        {"D", "CD0", "2.5"}, {"F", "CF0", "1.5f"}, {"Z", "CZT", "true"}, {"Z", "CZF", "false"},
        {"C", "CC0", "'A'"}, {"B", "CB0", "(byte) -7"}, {"S", "CS0", "(short) 300"},
    };

    private final Random r;

    ProgramGenerator(long seed) {
        r = new Random(seed);
    }

    /** A class {@code pkg.cls} with {@code members} members and a main that prints each one's result. */
    String program(String pkg, String cls, int members) {
        List<String> decls = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        for (String[] f : CONST_FIELDS) {
            decls.add("static final " + type(f[0].charAt(0)) + " " + f[1] + " = " + f[2] + ";");
        }
        String[] inits = {"(byte) 1", "(short) 1", "'x'", "2", "2L", "2f", "2.0", "true"};
        for (int i = 0; i < NAMES.length(); i++) {
            char t = NAMES.charAt(i);
            decls.add("static " + type(t) + " nc" + t + " = " + inits[i] + ";");
        }
        for (int i = 0; i < members; i++) {
            String[] m = member(cls, i);
            if (!m[0].isEmpty()) {
                decls.add(m[0]);
            }
            methods.add("static String m" + i + "() { " + m[1] + " }");
            calls.add("try { System.out.println(\"m" + i + "=\" + m" + i + "()); } "
                    + "catch (Throwable t) { System.out.println(\"m" + i + "!\" + t); }");
        }
        String helpers = "static String s(char c) { return \"c\" + (int) c; } "
                + "static String s(byte v) { return \"b\" + v; } static String s(short v) { return \"s\" + v; } "
                + "static String s(int v) { return \"i\" + v; } static String s(long v) { return \"j\" + v; } "
                + "static String s(float v) { return \"f\" + v; } static String s(double v) { return \"d\" + v; } "
                + "static String s(boolean v) { return \"z\" + v; }";
        StringBuilder b = new StringBuilder("package " + pkg + ";\n\nimport java.util.function.*;\n\n");
        b.append("public class ").append(cls).append(" {\n");
        for (String d : decls) b.append("    ").append(d).append('\n');
        b.append("    ").append(helpers).append('\n');
        for (String m : methods) b.append("    ").append(m).append('\n');
        b.append("    public static void main(String[] a) {\n");
        for (String c : calls) b.append("        ").append(c).append('\n');
        return b.append("    }\n}\n").toString();
    }

    private static String type(char t) {
        return switch (t) {
            case 'B' -> "byte";
            case 'S' -> "short";
            case 'C' -> "char";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'F' -> "float";
            case 'D' -> "double";
            default -> "boolean";
        };
    }

    private String choice(String... xs) {
        return xs[r.nextInt(xs.length)];
    }

    private char choice(String chars) {
        return chars.charAt(r.nextInt(chars.length()));
    }

    private int between(int lo, int hi) {
        return lo + r.nextInt(hi - lo + 1);
    }

    private String lit(char t) {
        return switch (t) {
            case 'Z' -> choice("true", "false");
            case 'I' -> choice("0", "1", "-1", "7", "300", "65", "2147483647", "(-2147483647 - 1)",
                    Integer.toString(between(-99, 99)));
            case 'J' -> choice("0L", "1L", "-1L", "9223372036854775807L", "(-9223372036854775807L - 1L)",
                    "4294967296L", between(-99, 99) + "L");
            case 'F' -> choice("0.0f", "-0.0f", "1.5f", "3.4e38f", "1e-45f", "Float.NaN", "Float.POSITIVE_INFINITY",
                    "2.75f");
            case 'D' -> choice("0.0", "-0.0", "2.5", "-3.75", "1e308", "4.9e-324", "Double.NaN",
                    "Double.NEGATIVE_INFINITY", "0.1");
            case 'C' -> choice("'A'", "(char) 0", "(char) 65535", "'z'", "(char) 200");
            case 'B' -> choice("(byte) 127", "(byte) -128", "(byte) 5", "(byte) 0");
            default -> choice("(short) 32767", "(short) -32768", "(short) 12");
        };
    }

    private String leaf(char t, boolean constant) {
        double x = r.nextDouble();
        if (!constant && x < 0.25) {
            return "nc" + t;
        }
        if (x < 0.45) {
            List<String> fields = new ArrayList<>();
            for (String[] f : CONST_FIELDS) {
                if (f[0].charAt(0) == t) fields.add(f[1]);
            }
            return fields.get(r.nextInt(fields.size()));
        }
        return lit(t);
    }

    private String nonzero(char t) {
        return switch (t) {
            case 'I' -> choice("3", "-7", "16", "1");
            case 'J' -> choice("3L", "-7L", "1000000007L");
            case 'F' -> choice("0.0f", "3.0f", "-1.5f");
            default -> choice("0.0", "3.0", "-1e-300");
        };
    }

    /** An expression of exactly type {@code t}. */
    private String expr(char t, int d, boolean c) {
        if (d <= 0 || r.nextDouble() < 0.2) {
            return leaf(t, c);
        }
        if (t == 'Z') {
            return switch (r.nextInt(7)) {
                case 0 -> "(" + expr(choice(NUM), d - 1, c) + " " + choice("<", "<=", ">", ">=", "==", "!=") + " "
                        + expr(choice(NUM), d - 1, c) + ")";
                case 1 -> "(" + expr('Z', d - 1, c) + " " + choice("&&", "||", "&", "|", "^", "==", "!=") + " "
                        + expr('Z', d - 1, c) + ")";
                case 2 -> "(!" + expr('Z', d - 1, c) + ")";
                case 3 -> "(" + expr('Z', d - 1, c) + " ? " + expr('Z', d - 1, c) + " : " + expr('Z', d - 1, c) + ")";
                default -> "(" + expr(choice("IJ"), d - 1, c) + " " + choice("<", "==", ">=") + " "
                        + expr('I', d - 1, c) + ")";
            };
        }
        if (t == 'B' || t == 'S' || t == 'C') {
            return "((" + type(t) + ") " + expr(choice(NUM), d - 1, c) + ")";
        }
        boolean integral = t == 'I' || t == 'J';
        int k = r.nextInt(9);
        if (k == 0) {
            return "((" + type(t) + ") " + expr(choice(NUM), d - 1, c) + ")";
        }
        if (k == 1) {
            return "(" + expr('Z', d - 1, c) + " ? " + expr(t, d - 1, c) + " : " + expr(t, d - 1, c) + ")";
        }
        if (k == 2) {
            return "(-(" + expr(t, d - 1, c) + "))";
        }
        if (k == 3 && integral) {
            return "(~" + expr(t, d - 1, c) + ")";
        }
        if (k == 4 && integral) {
            return "(" + expr(t, d - 1, c) + " " + choice("<<", ">>", ">>>") + " " + expr('I', d - 1, c) + ")";
        }
        if (k == 5 && integral) {
            return "(" + expr(t, d - 1, c) + " " + choice("&", "|", "^") + " " + expr(t, d - 1, c) + ")";
        }
        if (k == 6) {
            return "(" + expr(t, d - 1, c) + " " + choice("/", "%") + " " + nonzero(t) + ")";
        }
        if (k == 7) { // a narrower operand, promoted
            char narrow = choice(NUM.substring(0, NUM.indexOf(t) + 1));
            return "((" + type(t) + ") (" + expr(narrow, d - 1, c) + " " + choice("+", "-", "*") + " "
                    + expr(narrow, d - 1, c) + "))";
        }
        return "(" + expr(t, d - 1, c) + " " + choice("+", "-", "*") + " " + expr(t, d - 1, c) + ")";
    }

    private static boolean compare(int a, String op, int b) {
        return switch (op) {
            case "<" -> a < b;
            case "<=" -> a <= b;
            case ">" -> a > b;
            case ">=" -> a >= b;
            case "==" -> a == b;
            default -> a != b;
        };
    }

    /** A constant boolean expression whose value is {@code v}. */
    private String cbool(boolean v, int d) {
        int k = d > 0 ? r.nextInt(6) : 0;
        switch (k) {
            case 0: {
                int a = between(-9, 9);
                int b = between(-9, 9);
                List<String> ops = new ArrayList<>();
                for (String op : new String[] {"<", "<=", ">", ">=", "==", "!="}) {
                    if (compare(a, op, b) == v) ops.add(op);
                }
                String lhs = choice(Integer.toString(a), "(CI0 - (" + (7 - a) + "))", "(" + a + " + 0)");
                return "(" + lhs + " " + ops.get(r.nextInt(ops.size())) + " " + b + ")";
            }
            case 1:
                return "(!" + cbool(!v, d - 1) + ")";
            case 2:
                if (v) return "(" + cbool(true, d - 1) + " && " + cbool(true, d - 1) + ")";
                return r.nextBoolean()
                        ? "(" + cbool(r.nextBoolean(), d - 1) + " && " + cbool(false, d - 1) + ")"
                        : "(" + cbool(false, d - 1) + " && " + cbool(r.nextBoolean(), d - 1) + ")";
            case 3:
                if (!v) return "(" + cbool(false, d - 1) + " || " + cbool(false, d - 1) + ")";
                return "(" + cbool(true, d - 1) + " || " + cbool(r.nextBoolean(), d - 1) + ")";
            case 4: {
                boolean cond = r.nextBoolean();
                String then = cond ? cbool(v, d - 1) : cbool(r.nextBoolean(), d - 1);
                String other = cond ? cbool(r.nextBoolean(), d - 1) : cbool(v, d - 1);
                return "(" + cbool(cond, d - 1) + " ? " + then + " : " + other + ")";
            }
            default:
                return r.nextBoolean() ? (v ? "CZT" : "CZF") : (v ? "true" : "false");
        }
    }

    /** A method body legal only through definite assignment over a constant (JLS 16). */
    private String daMember() {
        int k = between(1, 99);
        return switch (r.nextInt(13)) {
            case 0 -> "int x; if (" + cbool(false, 2) + " || (x = " + k + ") > 0) return s(x); return \"n\";";
            case 1 -> "int x; if (" + cbool(true, 2) + " && (x = " + k + ") < 0) return \"n\"; else return s(x);";
            case 2 -> "int x; if (" + cbool(false, 2) + " && ncZ) return s(x); return \"n\";";
            case 3 -> "int x; boolean r = " + cbool(true, 2) + " && ((x = " + k + ") > 0); return s(x) + r;";
            case 4 -> "int x; int y = (" + cbool(false, 2) + " && ncZ) ? x : " + k + "; return s(y);";
            case 5 -> "int x; while (" + cbool(true, 2) + " || ncZ) { x = " + k + "; break; } return s(x);";
            case 6 -> "final boolean t = " + cbool(true, 2) + "; int x; if (t && (x = " + k
                    + ") > 0) return s(x); else return s(x);";
            case 7 -> "int x; if (!" + cbool(true, 2) + " || (x = " + k + ") > 0) return s(x); return \"n\";";
            case 8 -> "int x; if (ncZ ? (x = " + k + ") > 0 : " + cbool(false, 2) + ") return s(x); return \"n\";";
            case 9 -> "int x; if (switch (ncI) { case 1 -> " + cbool(false, 2) + "; default -> (x = " + k
                    + ") > 0; }) return s(x); return \"n\";";
            case 10 -> "int x; for (; " + cbool(true, 2) + " || ncZ; ) { x = " + k + "; break; } return s(x);";
            case 11 -> "int x; if ((ncZ && " + cbool(false, 2) + ") || (x = " + k + ") > 0) return s(x); return \"n\";";
            default -> "int x; boolean r = (" + cbool(false, 2) + " || ncZ) ? true : (" + cbool(true, 2)
                    + " && (x = " + k + ") > 0); return s(r);";
        };
    }

    /** {class-level declarations, method body} for member {@code i}. */
    private String[] member(String cls, int i) {
        char t = choice(NAMES);
        int d = between(1, 4);
        boolean c = r.nextDouble() < 0.8;
        int k = r.nextInt(14);
        char st = choice("IJDZ");
        String sup = switch (st) {
            case 'I' -> "IntSupplier";
            case 'J' -> "LongSupplier";
            case 'D' -> "DoubleSupplier";
            default -> "BooleanSupplier";
        };
        String get = switch (st) {
            case 'I' -> "getAsInt";
            case 'J' -> "getAsLong";
            case 'D' -> "getAsDouble";
            default -> "getAsBoolean";
        };
        String jt = type(t);
        return switch (k) {
            case 0 -> new String[] {"", "return s(" + expr(t, d, c) + ");"};
            case 1 -> new String[] {"", jt + " v = " + expr(t, d, c) + "; return s(v);"};
            case 2 -> new String[] {"", "final " + jt + " c = " + expr(t, d, true) + "; " + jt
                    + " v = c; return s(v) + s(" + expr(t, 1, c) + ");"};
            case 3 -> new String[] {"static " + jt + " f" + i + " = " + expr(t, d, c) + ";", "return s(f" + i + ");"};
            case 4 -> new String[] {jt + " g" + i + " = " + expr(t, d, c) + ";", "return s(new " + cls + "().g" + i + ");"};
            case 5 -> new String[] {"", sup + " l = () -> " + expr(st, d, c) + "; return s(l." + get + "());"};
            case 6 -> new String[] {"static " + sup + " l" + i + " = () -> " + expr(st, d, c) + ";",
                "return s(l" + i + "." + get + "());"};
            case 7 -> new String[] {"static " + jt + " b" + i + "; static { b" + i + " = " + expr(t, d, c) + "; }",
                "return s(b" + i + ");"};
            case 8 -> new String[] {jt + " h" + i + "; { h" + i + " = " + expr(t, d, c) + "; }",
                "return s(new " + cls + "().h" + i + ");"};
            case 9 -> new String[] {"", jt + " v = switch (ncI) { case 1 -> " + expr(t, d, c) + "; case 2 -> "
                    + expr(t, d, c) + "; default -> " + expr(t, d, c) + "; }; return s(v);"};
            case 10 -> new String[] {"", "if (" + expr('Z', d, c) + ") return \"t\"; else return \"f\";"};
            case 11 -> {
                char nt = choice("BSCIJ");
                yield new String[] {"", type(nt) + " v = " + lit(nt) + "; v " + choice("+=", "-=", "*=", "<<=", "^=")
                        + " " + expr(choice("IJ"), d, c) + "; return s(v);"};
            }
            case 12 -> new String[] {"", "class L { " + jt + " f() { return " + expr(t, d, c) + "; } } return s(new L().f());"};
            default -> new String[] {"", daMember()};
        };
    }
}
