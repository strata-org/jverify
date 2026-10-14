package org.strata.java.bytecode.unfolding;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Constant unfolding keeps a program's meaning: compiled plainly and unfolded, a program prints the
 * same, and the JVM's verifier accepts both classfiles.
 */
class PreservationTest {
    /**
     * Folds: constant expressions in every context the rewrite treats differently. DA: methods that
     * are legal only because javac's definite assignment uses a constant's value. Contexts: lambdas
     * in field initializers, initializer blocks, enum arguments, interface fields, records. Captures:
     * constant locals read by local and anonymous classes, and blank locals in field initializers,
     * where definite assignment relies on a constant condition.
     */
    @ParameterizedTest
    @ValueSource(strings = {"p.Folds", "p.DA", "p.Contexts", "p.Captures"})
    void handWrittenProgramsPrintTheSame(String className, @TempDir Path tmp) throws Exception {
        Path src = Path.of(PreservationTest.class.getResource("/probes").toURI());
        Programs.compile(src, tmp.resolve("plain"), false);
        Programs.compile(src, tmp.resolve("unfolded"), true);
        String plain = Programs.run(tmp.resolve("plain"), className);
        assertFalse(plain.isBlank(), className);
        assertEquals(plain, Programs.run(tmp.resolve("unfolded"), className), className);
    }

    /** 450 random members per seed: every primitive type, every context, definite-assignment shapes. */
    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void generatedProgramsPrintTheSame(long seed, @TempDir Path tmp) throws Exception {
        var generator = new ProgramGenerator(seed);
        int classes = 15;
        for (int i = 0; i < classes; i++) {
            Programs.write(tmp.resolve("src"), "g/G" + i + ".java", generator.program("g", "G" + i, 30));
        }
        Programs.compile(tmp.resolve("src"), tmp.resolve("plain"), false);
        Programs.compile(tmp.resolve("src"), tmp.resolve("unfolded"), true);
        for (int i = 0; i < classes; i++) {
            String className = "g.G" + i;
            assertEquals(Programs.run(tmp.resolve("plain"), className),
                    Programs.run(tmp.resolve("unfolded"), className), "seed " + seed + ", " + className);
        }
    }
}
