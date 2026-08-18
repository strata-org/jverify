package org.strata.jverify.verifier;

import com.amazon.ion.IonContainer;
import com.amazon.ion.IonSexp;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSymbol;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import org.junit.jupiter.api.Test;
import org.strata.jverify.testengine.JVerifyTestEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: every composite sort declares its Java supertype closure as
 * {@code extends} edges, ancestors first, so Strata's transitive subtype check accepts a
 * value wherever Java subtyping does. A lambda / method reference is one case: its
 * synthetic sort ({@code lambda.<line>_<col>}) extends its functional-interface sort,
 * declared before the child.
 *
 * <p>Without the edge the two sorts are unrelated on the lattice, and the
 * body-vs-declared-output check rejects {@code return HashMap::new} against a declared
 * {@code Supplier} return ("expected 'java.util.function.Supplier', got 'lambda.N_M'").
 * LambdaToAnonymousClassCompiler already records the interface on the synthetic class's
 * ClassType {@code interfaces} list; the edge exposes that to the lattice, which walks
 * {@code extends} transitively.
 */
public class LambdaSortExtendsEmitTest {

    @Test
    public void lambdaSortDeclaredExtendingItsFunctionalInterface() throws Exception {
        String source = """
                import java.util.HashMap;
                import java.util.function.Supplier;
                class Refs {
                    Supplier<HashMap<String, String>> asMap() {
                        return HashMap::new;
                    }
                }
                """;
        IonValue emittedProgram = emitLaurel("Refs.java", source);

        // The lambda sort's declaration carries an extends edge naming the
        // functional interface.
        IonSexp lambdaComposite = findCompositeDeclaring(emittedProgram, "lambda.");
        assertNotNull(lambdaComposite,
                "the emitted program should declare the synthetic lambda.* composite sort");
        IonValue extendsOp = extendingOf(lambdaComposite);
        assertNotNull(extendsOp,
                "the synthetic lambda sort must be declared `extends` its functional interface");
        assertTrue(containsIdent(extendsOp, "java.util.function.Supplier"),
                "the extends edge should name java.util.function.Supplier");

        // The parent is declared, before the child, so the resolver sees it
        // when it processes the edge.
        int parentIdx = indexOfCompositeDeclaring(emittedProgram, "java.util.function.Supplier");
        int childIdx = indexOfCompositeDeclaring(emittedProgram, "lambda.");
        assertTrue(parentIdx >= 0, "the Supplier composite sort should be declared");
        assertTrue(parentIdx < childIdx,
                "the parent sort must be declared before the extending child sort");
    }

    /**
     * Discrimination coverage for the edge emission. Two lambdas of DIFFERENT
     * functional interfaces each extend their own interface, no cross-wiring. A
     * USER-WRITTEN anonymous class gets a UNIQUE, non-empty sort — its flatname
     * {@code Refs2.1}; the empty qualified name would collide all of them onto
     * one sort — with edges to its OWN supertypes. An interface sort extends
     * {@code Object}: javac gives interfaces no superclass, but JLS 4.10.2 makes
     * Object their supertype, and without the edge an interface-typed value
     * cannot flow to an Object-typed position.
     */
    @Test
    public void distinctInterfacesAndUserAnonymousClassExtendsItsSupertype() throws Exception {
        String source = """
                import java.util.function.Supplier;
                import java.util.function.BooleanSupplier;
                class Refs2 {
                    Supplier<String> a() {
                        return () -> "s";
                    }
                    BooleanSupplier b() {
                        return () -> true;
                    }
                    Runnable c() {
                        return new Runnable() {
                            @Override public void run() { }
                        };
                    }
                    Supplier<String> d() {
                        return new Supplier<String>() {
                            @Override public String get() { return "t"; }
                        };
                    }
                }
                """;
        IonValue emittedProgram = emitLaurel("Refs2.java", source);

        List<IonSexp> composites = new ArrayList<>();
        collectComposites(emittedProgram, composites);

        // Each synthetic lambda sort extends exactly its OWN interface.
        int supplierLambdas = 0, booleanSupplierLambdas = 0;
        for (IonSexp composite : composites) {
            if (!compositeName(composite).startsWith("lambda.")) {
                continue;
            }
            IonValue edge = extendingOf(composite);
            assertNotNull(edge, "every lambda sort should carry an extends edge: "
                    + compositeName(composite));
            boolean isSupplier = containsIdent(edge, "java.util.function.Supplier");
            boolean isBooleanSupplier = containsIdent(edge, "java.util.function.BooleanSupplier");
            assertTrue(isSupplier ^ isBooleanSupplier,
                    "a lambda sort should extend exactly one of the two interfaces: "
                            + compositeName(composite));
            if (isSupplier) supplierLambdas++;
            if (isBooleanSupplier) booleanSupplierLambdas++;
        }
        assertEquals(1, supplierLambdas, "one lambda should extend Supplier");
        assertEquals(1, booleanSupplierLambdas, "one lambda should extend BooleanSupplier");

        // A user-written class carries edges to its OWN supertype closure, from
        // its symbol. Refs2, a plain class, extends only java.lang.Object, neither
        // functional interface: distinct sorts never share a parent list.
        IonSexp refs2 = findCompositeDeclaring(emittedProgram, "Refs2");
        assertNotNull(refs2, "the Refs2 composite sort should be declared");
        assertEquals("Refs2", compositeName(refs2));
        IonValue refs2Edge = extendingOf(refs2);
        assertNotNull(refs2Edge, "Refs2 must extend its supertype (java.lang.Object)");
        assertTrue(containsIdent(refs2Edge, "java.lang.Object"),
                "Refs2's extends edge should name java.lang.Object");
        assertFalse(containsIdent(refs2Edge, "java.util.function.Supplier")
                        || containsIdent(refs2Edge, "java.util.function.BooleanSupplier"),
                "a user class must not inherit a lambda's functional-interface edge");

        // Each user-written anonymous class gets a UNIQUE, NON-EMPTY sort named
        // by its flatname, with edges to its OWN supertypes. Its qualified name
        // is empty; one shared ""-named sort would keep only the FIRST such
        // class's edges and reject every other one's flows.
        IonSexp anonRunnable = findCompositeDeclaring(emittedProgram, "Refs2.1");
        assertNotNull(anonRunnable,
                "the first anonymous class should declare sort 'Refs2.1' (its flatname)");
        IonValue anonRunnableEdge = extendingOf(anonRunnable);
        assertNotNull(anonRunnableEdge, "the anonymous sort must extend its supertypes");
        assertTrue(containsIdent(anonRunnableEdge, "java.lang.Runnable"),
                "the first anonymous sort's edges should include java.lang.Runnable");
        IonSexp anonSupplier = findCompositeDeclaring(emittedProgram, "Refs2.2");
        assertNotNull(anonSupplier,
                "the second anonymous class should declare sort 'Refs2.2', not collide with the first");
        IonValue anonSupplierEdge = extendingOf(anonSupplier);
        assertNotNull(anonSupplierEdge, "the second anonymous sort must extend its supertypes");
        assertTrue(containsIdent(anonSupplierEdge, "java.util.function.Supplier"),
                "the second anonymous sort's edges should include Supplier, not Runnable");

        // An interface sort extends Object (JLS 4.10.2): no javac superclass, so
        // the parent is added explicitly, or an interface-typed value cannot flow
        // to an Object-typed position.
        IonSexp runnable = findCompositeDeclaring(emittedProgram, "java.lang.Runnable");
        assertNotNull(runnable, "the Runnable composite sort should be declared");
        IonValue runnableEdge = extendingOf(runnable);
        assertNotNull(runnableEdge, "an interface sort must extend java.lang.Object");
        assertTrue(containsIdent(runnableEdge, "java.lang.Object"),
                "Runnable's extends edge should name java.lang.Object");
    }

    /**
     * Emit-only run over one in-memory source file, returning the parsed Laurel Ion.
     * The temp file is deleted before return; the {@link IonValue} is self-contained.
     */
    private static IonValue emitLaurel(String fileName, String source) throws Exception {
        var sourceFile = new SourceFile(Path.of(fileName), source);
        Path emittedLaurel = Files.createTempFile("emitted-laurel-extends", ".ion");
        Files.deleteIfExists(emittedLaurel);
        try {
            var baseOptions = JVerifyTestEngine.getVerifierOptions(
                    JVerifyTestEngine.makeJVerifyTestAnnotation(1, 0), null);
            var emitOptions = EmitLaurelWorkflowTest.withEmit(
                    baseOptions, emittedLaurel, /* emitLaurelOnly */ true);
            var emitResult = Driver.getDriver(emitOptions)
                    .verifyJavaFiles(new ArrayList<>(List.of(sourceFile)));
            assertEquals(0, emitResult.exitCode(), "the emit-only run should succeed");

            IonSystem ion = IonSystemBuilder.standard().build();
            return ion.singleValue(Files.readAllBytes(emittedLaurel));
        } finally {
            Files.deleteIfExists(emittedLaurel);
        }
    }

    /** DFS for the first {@code Composite} op whose declared name starts with {@code namePrefix}. */
    private static IonSexp findCompositeDeclaring(IonValue value, String namePrefix) {
        if (value instanceof IonSexp sexp && !sexp.isEmpty()
                && sexp.get(0) instanceof IonSymbol sym
                && "Composite".equals(sym.stringValue())
                && compositeName(sexp).startsWith(namePrefix)) {
            return sexp;
        }
        if (value instanceof IonContainer container) {
            for (IonValue child : container) {
                IonSexp found = findCompositeDeclaring(child, namePrefix);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Position of the first {@code Composite} op declaring a name with
     * this prefix, in document order of all composite ops; -1 if absent.
     */
    private static int indexOfCompositeDeclaring(IonValue program, String namePrefix) {
        List<IonSexp> composites = new ArrayList<>();
        collectComposites(program, composites);
        for (int i = 0; i < composites.size(); i++) {
            if (compositeName(composites.get(i)).startsWith(namePrefix)) {
                return i;
            }
        }
        return -1;
    }

    private static void collectComposites(IonValue value, List<IonSexp> out) {
        if (value instanceof IonSexp sexp && !sexp.isEmpty()
                && sexp.get(0) instanceof IonSymbol sym
                && "Composite".equals(sym.stringValue())) {
            out.add(sexp);
        }
        if (value instanceof IonContainer container) {
            for (IonValue child : container) {
                collectComposites(child, out);
            }
        }
    }

    /** The declared name of a {@code Composite} op: its payload struct's {@code name.text}. */
    private static String compositeName(IonSexp composite) {
        if (composite.size() >= 2 && composite.get(1) instanceof IonStruct payload
                && payload.get("name") instanceof IonStruct name
                && name.get("text") instanceof com.amazon.ion.IonText text) {
            return text.stringValue();
        }
        return "";
    }

    /**
     * A {@code Composite} op's {@code extending} field, or {@code null} when the sort
     * declares no parents. An EMPTY list counts as absent: that is what the emitter
     * produced before this change, the state these assertions rule out.
     */
    private static IonValue extendingOf(IonSexp composite) {
        if (composite.size() >= 2 && composite.get(1) instanceof IonStruct payload
                && payload.get("extending") instanceof IonContainer parents
                && !parents.isEmpty()) {
            return (IonValue) parents;
        }
        return null;
    }

    /** Whether any descendant is an identifier or text node with this value. */
    private static boolean containsIdent(IonValue value, String ident) {
        if (value instanceof com.amazon.ion.IonText text
                && ident.equals(text.stringValue())) {
            return true;
        }
        if (value instanceof IonContainer container) {
            for (IonValue child : container) {
                if (containsIdent(child, ident)) {
                    return true;
                }
            }
        }
        return false;
    }
}
