package org.strata.jverify.verifier;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.tree.JCTree;
import org.junit.jupiter.api.Test;
import org.strata.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code hasImplementation} must answer, not throw, for a body with fewer than two
 * statements -- and must still recognise the wrapped shape.
 *
 * <p>The original form was
 * {@code method.body.getStatements().get(1) instanceof JCBlock}: unguarded index 1, and
 * {@code com.sun.tools.javac.util.List.get} throws {@code IndexOutOfBoundsException} on a
 * shorter list. Its sibling {@code getImplementationBlock} is the same logic with a
 * {@code size() < 2} guard, so two spellings of one question disagreed.
 *
 * <p>No verifier fixture can reach the failing case: the sole caller runs after every body
 * has been rewritten to exactly [contract, implementation], so size is always >= 2 by
 * then. Only pass ordering held the crash off, undeclared and unasserted -- hence a unit
 * test, on trees from a real parse (javac's tree constructors are protected, and
 * TreeMaker needs an initialised Symtab).
 *
 * <p>{@code wrapped} is the load-bearing positive case: without it a
 * constant-{@code false} implementation would pass every other assertion.
 */
public class HasImplementationTest {

    private static final String SOURCE = """
            class Bodies {
                void zero() { }
                int one() { return 1; }
                int two() { int x = 1; return x; }
                void wrapped() { ; { int y = 1; } }
            }
            """;

    /** Expected answer per method name, keyed by its body's statement shape. */
    private static final Map<String, Boolean> EXPECTED = Map.of(
            "zero", false,     // 0 statements -- indexed out of range before the guard
            "one", false,      // 1 statement  -- indexed out of range before the guard
            "two", false,      // 2 statements, but statements[1] is a `return`
            "wrapped", true);  // 2 statements and statements[1] IS a block

    private static List<JCTree.JCMethodDecl> parseMethods() throws Exception {
        var file = new SimpleJavaFileObject(URI.create("string:///Bodies.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return SOURCE;
            }
        };
        var compiler = ToolProvider.getSystemJavaCompiler();
        var task = (JavacTask) compiler.getTask(null, null, d -> { }, null, null, List.of(file));
        var unit = (JCTree.JCCompilationUnit) task.parse().iterator().next();
        var clazz = (JCTree.JCClassDecl) unit.getTypeDecls().get(0);
        return clazz.getMembers().stream()
                .filter(m -> m instanceof JCTree.JCMethodDecl)
                .map(m -> (JCTree.JCMethodDecl) m)
                .toList();
    }

    @Test
    public void answersEveryBodyShapeWithoutThrowing() throws Exception {
        var methods = parseMethods();
        assertEquals(EXPECTED.size(), methods.size(),
                "expected one method per entry in EXPECTED");

        for (var m : methods) {
            String name = m.name.toString();
            Boolean expected = EXPECTED.get(name);
            assertNotNull(expected, "unexpected method in the probe source: " + name);
            assertNotNull(m.body, "each probe method has a body");
            if (expected) {
                assertTrue(MethodOrLoopContractCompiler.hasImplementation(m),
                        "statements[1] is a block, so this must report an implementation: " + name);
            } else {
                assertFalse(MethodOrLoopContractCompiler.hasImplementation(m),
                        "no implementation block here -- must answer false, not throw: " + name);
            }
        }
    }
}
