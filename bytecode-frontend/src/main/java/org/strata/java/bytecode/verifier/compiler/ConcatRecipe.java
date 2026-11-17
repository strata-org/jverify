package org.strata.java.bytecode.verifier.compiler;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The recipe of a {@code StringConcatFactory} bootstrap, javac's lowering of
 * the {@code +} operator on strings (JLS 15.18.1). In the recipe of
 * {@code makeConcatWithConstants}, {@code \u0001} stands for the next dynamic
 * argument, {@code \u0002} for the next constant among the bootstrap's further
 * arguments, and every other character is literal text; javac moves a
 * constant into the bootstrap arguments only when its text contains one of
 * the two tags. {@code makeConcat} has no recipe: the result is the
 * concatenation of the arguments.
 */
final class ConcatRecipe {

    private ConcatRecipe() {
    }

    /** One operand of the concatenation: literal text or the dynamic argument at {@code index}. */
    sealed interface Part {
        record Text(String text) implements Part {
        }

        record Arg(int index) implements Part {
        }
    }

    /**
     * The operands in order, adjacent literal texts merged into one.
     *
     * @param recipe    the recipe, or null for {@code makeConcat}
     * @param argCount  the number of dynamic arguments
     * @param constants the bootstrap's constant arguments after the recipe, as text
     * @throws IllegalArgumentException when the recipe's tags do not match the arguments given
     */
    static List<Part> parts(@Nullable String recipe, int argCount, List<String> constants) {
        var parts = new ArrayList<Part>();
        var text = new StringBuilder();
        int arg = 0;
        int constant = 0;
        if (recipe == null) {
            for (; arg < argCount; arg++) {
                parts.add(new Part.Arg(arg));
            }
            return parts;
        }
        for (int i = 0; i < recipe.length(); i++) {
            char c = recipe.charAt(i);
            if (c == '\u0001') {
                if (arg >= argCount) {
                    throw new IllegalArgumentException("recipe names more arguments than the call site has");
                }
                if (!text.isEmpty()) {
                    parts.add(new Part.Text(text.toString()));
                    text.setLength(0);
                }
                parts.add(new Part.Arg(arg++));
            } else if (c == '\u0002') {
                if (constant >= constants.size()) {
                    throw new IllegalArgumentException("recipe names more constants than the bootstrap has");
                }
                text.append(constants.get(constant++));
            } else {
                text.append(c);
            }
        }
        if (arg != argCount) {
            throw new IllegalArgumentException("recipe names fewer arguments than the call site has");
        }
        if (!text.isEmpty()) {
            parts.add(new Part.Text(text.toString()));
        }
        return parts;
    }
}
