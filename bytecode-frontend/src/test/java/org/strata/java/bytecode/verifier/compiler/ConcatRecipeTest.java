package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.strata.java.bytecode.verifier.compiler.ConcatRecipe.Part.Arg;
import static org.strata.java.bytecode.verifier.compiler.ConcatRecipe.Part.Text;

/** The recipes javac emits for {@code +} on strings, as {@code javap -v} shows them. */
class ConcatRecipeTest {

    @Test
    void argumentsAndTextInterleave() {
        assertEquals(List.of(new Arg(0), new Arg(1)), ConcatRecipe.parts("\u0001\u0001", 2, List.of()));
        assertEquals(List.of(new Arg(0), new Text("cd")), ConcatRecipe.parts("\u0001cd", 1, List.of()));
        assertEquals(List.of(new Text("a="), new Arg(0), new Text(", b="), new Arg(1)),
                ConcatRecipe.parts("a=\u0001, b=\u0001", 2, List.of()));
    }

    @Test
    void aNullOperandIsFoldedByJavacIntoTheText() {
        assertEquals(List.of(new Text("xnull")), ConcatRecipe.parts("xnull", 0, List.of()));
    }

    @Test
    void aTaggedConstantIsReadFromTheBootstrapArgumentsAndMergedWithItsNeighbours() {
        assertEquals(List.of(new Text("a\u0001b"), new Arg(0)),
                ConcatRecipe.parts("a\u0002b\u0001", 1, List.of("\u0001")));
    }

    @Test
    void makeConcatHasNoRecipe() {
        assertEquals(List.of(new Arg(0), new Arg(1), new Arg(2)), ConcatRecipe.parts(null, 3, List.of()));
        assertEquals(List.of(), ConcatRecipe.parts(null, 0, List.of()));
    }

    @Test
    void aRecipeThatDoesNotMatchTheCallSiteIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ConcatRecipe.parts("\u0001\u0001", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ConcatRecipe.parts("\u0001", 2, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ConcatRecipe.parts("\u0002", 0, List.of()));
    }
}
