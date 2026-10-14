package org.strata.java.bytecode.unfolding;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.Opcode;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What constant unfolding emits, and what it leaves folded, at the level of opcodes. */
class UnfoldingTest {
    private static final String SOURCE = """
            package u;

            class U {
                static final int A = 1;
                static boolean flag = true;

                static int arithmetic() {
                    return 7 / 2 + (int) 3.9 + -(1) + (byte) (300);
                }

                static int local() {
                    final int k = 7 / 2;
                    return k;
                }

                // A value context that mentions no local declared without an initializer.
                static boolean value() {
                    return (A > 0) && !(A < 0);
                }

                static boolean literals() {
                    return (true && false) || !true;
                }

                static int choice() {
                    return true ? 7 : 9;
                }

                static boolean mixed() {
                    return false && flag;
                }

                static int mixedChoice() {
                    return true ? 1 : (flag ? 2 : 3);
                }

                // x is definitely assigned after the expression only because A > 0 is true.
                static int assigned() {
                    int x;
                    boolean r = (A > 0) && ((x = 4) > 0);
                    return x;
                }

                static int ifCondition() {
                    if (7 / 2 == 3) return 1;
                    return 0;
                }

                static void asserted() {
                    assert 7 / 2 == 3;
                }

                static String string() {
                    return "hel" + "lo";
                }
            }
            """;

    @TempDir
    static Path tmp;

    private static Path plain;
    private static Path unfolded;

    @BeforeAll
    static void compile() throws IOException {
        Programs.write(tmp.resolve("src"), "u/U.java", SOURCE);
        Programs.compile(tmp.resolve("src"), tmp.resolve("plain"), false);
        Programs.compile(tmp.resolve("src"), tmp.resolve("unfolded"), true);
        plain = tmp.resolve("plain/u/U.class");
        unfolded = tmp.resolve("unfolded/u/U.class");
    }

    /** Conditional branches, but for null tests. */
    private static long compares(Path classFile, String method) throws IOException {
        return Programs.opcodes(classFile, method).stream()
                .filter(o -> o.name().startsWith("IF") && !o.name().endsWith("NULL"))
                .count();
    }

    @Test
    void operationsAndConversionsAreEmitted() throws IOException {
        assertFalse(Programs.opcodes(plain, "arithmetic").contains(Opcode.IDIV));
        var ops = Programs.opcodes(unfolded, "arithmetic");
        assertTrue(ops.containsAll(List.of(Opcode.IDIV, Opcode.D2I, Opcode.IADD, Opcode.INEG, Opcode.I2B)),
                ops::toString);
    }

    /** k is local 0. */
    @Test
    void aFinalLocalIsLoadedNotInlined() throws IOException {
        assertFalse(Programs.opcodes(plain, "local").contains(Opcode.ILOAD_0));
        assertTrue(Programs.opcodes(unfolded, "local").contains(Opcode.ILOAD_0));
    }

    @Test
    void conditionalOperatorsAreEmittedInAValueContext() throws IOException {
        assertEquals(0, compares(plain, "value"));
        assertEquals(2, compares(unfolded, "value"));
    }

    /** One conditional branch per constant operand of &&, ||, ! and ?:, also beside a non-constant one. */
    @Test
    void eachConstantBooleanOperandIsBranchedOn() throws IOException {
        assertEquals(0, compares(plain, "literals"));
        assertEquals(3, compares(unfolded, "literals"));
        assertEquals(1, compares(unfolded, "choice"));
        assertEquals(0, compares(plain, "mixed"));
        assertEquals(2, compares(unfolded, "mixed"));
        assertEquals(0, compares(plain, "mixedChoice"));
        assertEquals(2, compares(unfolded, "mixedChoice"));
    }

    @Test
    void statementConditionsStayFolded() throws IOException {
        for (String method : List.of("ifCondition", "asserted")) {
            assertEquals(Programs.opcodes(plain, method), Programs.opcodes(unfolded, method), method);
        }
    }

    @Test
    void aConstantFieldStaysInlined() throws IOException {
        assertFalse(Programs.opcodes(unfolded, "value").contains(Opcode.GETSTATIC));
    }

    @Test
    void aConstantDefiniteAssignmentUsesStaysFolded() throws IOException {
        assertEquals(compares(plain, "assigned"), compares(unfolded, "assigned"));
    }

    @Test
    void aStringConstantStaysInterned() throws IOException {
        assertEquals(Programs.opcodes(plain, "string"), Programs.opcodes(unfolded, "string"));
    }
}
