package org.strata.jverify.verifier.compiler.generator.laurel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Names;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the refusal POLICY, exercised without running the translator.
 *
 * <p>This is the point of gates reporting a reason instead of throwing: a refusal is
 * decided per javac SHAPE, and the shapes a fixture suite happens to contain are not the
 * shapes that exist. Both blocking findings on CR-291743936 were shapes no fixture
 * covered. Enumerating shapes through the full pipeline costs a Java fixture and a
 * multi-minute suite run each; here it costs a few javac symbols.
 *
 * <p>Covered here: {@code receiverRefusal}, which matches on tree shape and needs no type
 * hierarchy. The two overload gates do — they walk populated member scopes — and are left
 * to the fixtures for now. ({@code dispatchRefusal} used to be covered here too; it was
 * deleted when Laurel gained runtime dispatch, so there is no longer a gate to test.)
 *
 * <p>Passing {@code null} for a methodSelect is safe rather than lucky: the gate reaches it
 * only through an {@code instanceof JCFieldAccess} test, which is false for null.
 */
class RefusalGatesTest {

    private final Context context = newContext();
    private final Names names = Names.instance(context);

    /** {@code TreeMaker.instance} transitively initialises {@code Modules}, which
        dereferences a {@code JavaFileManager}; pre-registering one keeps an otherwise bare
        Context usable for building trees without standing up a compiler. */
    private static Context newContext() {
        var c = new Context();
        JavacFileManager.preRegister(c);
        return c;
    }

    /** A class symbol carrying only the flags the gate reads. `enclClass()` on a method
        returns its owner directly, so the class's own owner is never dereferenced. */
    private Symbol.ClassSymbol cls(String name, long flags) {
        return new Symbol.ClassSymbol(flags, names.fromString(name), null);
    }

    private Symbol.MethodSymbol method(String name, long flags, Symbol.ClassSymbol owner) {
        return new Symbol.MethodSymbol(flags, names.fromString(name), Type.noType, owner);
    }

    // ---- receiverRefusal: the gate untangled out of `receiverSelf` ----

    @Test
    void implicitThisReceiverIsAlwaysFine() {
        // An implicit-this call `m(...)` has no receiver expression at all.
        assertEquals(Optional.empty(), RefusalGates.receiverRefusal(null),
                "no receiver expression means nothing to refuse");
    }

    @Test
    void superCallIsRefusedWithItsOwnReason() {
        var trees = TreeMaker.instance(context);
        var superIdent = trees.Ident(names._super);
        var select = trees.Select(superIdent, names.fromString("m"));
        var refusal = RefusalGates.receiverRefusal(select);
        assertTrue(refusal.isPresent(), "super.m() cannot be lowered");
        assertEquals("super call is not yet supported", refusal.get(),
                "the specific cause, not the general dispatch refusal");
    }

    @Test
    void freshlyAllocatedReceiverIsRefusedThroughParens() {
        var trees = TreeMaker.instance(context);
        var alloc = trees.NewClass(null, com.sun.tools.javac.util.List.nil(),
                trees.Ident(names.fromString("T")), com.sun.tools.javac.util.List.nil(), null);
        // Wrapped in parens, so this also pins that the gate unwraps them.
        var select = trees.Select(trees.Parens(alloc), names.fromString("m"));
        var refusal = RefusalGates.receiverRefusal(select);
        assertTrue(refusal.isPresent(), "(new T()).m() cannot be lowered");
        assertEquals("method call on a freshly-allocated receiver is not yet supported",
                refusal.get(), "parens must be unwrapped before the shape test");
    }
}
