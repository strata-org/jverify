package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.strata.jverify.laurel.TypeDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolated tests for the composite supertype-closure helpers in
 * {@link BytecodeToLaurelCompiler}. No program classfiles are registered, so
 * every lookup takes the reflection path against the JCL -- which lets a defect
 * in one helper surface here directly instead of only as a downstream corpus
 * failure that is slower to attribute.
 */
class ClosureHelpersTest {

    private final BytecodeToLaurelCompiler compiler = new BytecodeToLaurelCompiler(new Reporter());

    @Test
    void sortNameForFqn_mapsObjectAndFlattensNestedNames() {
        assertEquals("Object", compiler.sortNameForFqn("java.lang.Object"));
        assertEquals("java.util.Map.Entry", compiler.sortNameForFqn("java.util.Map$Entry"));
        assertEquals("com.example.Foo", compiler.sortNameForFqn("com.example.Foo"));
    }

    @Test
    void loadClassReflectively_recoversNestedBinaryNameFromDottedSortName() {
        // Sort names have '$' flattened to '.', but Class.forName needs '$' for
        // a nested class; the helper must recover it.
        assertEquals(java.util.Map.Entry.class, compiler.loadClassReflectively("java.util.Map.Entry"));
        assertEquals(String.class, compiler.loadClassReflectively("java.lang.String"));
        assertNull(compiler.loadClassReflectively("no.such.Type"));
    }

    @Test
    void isInterface_usesReflectionForLibraryTypes() {
        assertTrue(compiler.isInterface("java.util.List"));
        assertTrue(compiler.isInterface("java.util.Map.Entry"));   // nested interface
        assertFalse(compiler.isInterface("java.lang.String"));
        assertFalse(compiler.isInterface("no.such.Type"));
    }

    @Test
    void directSupertypeSorts_superclassFirstThenInterfacesDeduped() {
        List<String> supers = compiler.directSupertypeSorts("java.util.ArrayList");
        assertEquals("java.util.AbstractList", supers.get(0), "superclass comes first");
        assertTrue(supers.contains("java.util.List"), "interfaces included: " + supers);
        assertEquals(supers.size(), new HashSet<>(supers).size(), "no duplicate edges: " + supers);
    }

    @Test
    void directSupertypeSorts_superinterfacelessInterfaceHasNoDirectSupertype() {
        // Object is supplied by emitCompositeClosure's fallback, not here
        // (JLS 4.10.2), matching javac's getSuperclass()==noType.
        assertTrue(compiler.directSupertypeSorts("java.lang.Runnable").isEmpty());
    }

    @Test
    void emitCompositeClosure_emitsObjectExactlyOnceAcrossSharedFallback() {
        var emitted = new HashSet<String>();
        var out = new ArrayList<TypeDefinition>();
        // Two marker interfaces with no superinterfaces: both fall back to Object.
        compiler.emitCompositeClosure("java.lang.Runnable", emitted, out);
        compiler.emitCompositeClosure("java.util.EventListener", emitted, out);
        assertTrue(emitted.contains("Object"));
        assertTrue(emitted.contains("java.lang.Runnable"));
        assertTrue(emitted.contains("java.util.EventListener"));
        // Ancestors-first, Object shared and emitted once: [Object, Runnable, EventListener].
        assertEquals(3, out.size(), "Object emitted exactly once: " + out.size());
    }
}
