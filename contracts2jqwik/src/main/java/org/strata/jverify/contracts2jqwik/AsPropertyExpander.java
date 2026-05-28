package org.strata.jverify.contracts2jqwik;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;

/**
 * Walks a class declaration and, for each method annotated with {@code @AsProperty},
 * appends a synthesized sibling method annotated with jqwik's {@code @Property}.
 *
 * <p>The synthesized method is built from the original method's signature and the
 * {@code precondition} / {@code postcondition} calls in its body. The translation
 * is purely structural — every clause is emitted verbatim except where it cannot
 * be executed at runtime (quantifiers, {@code old(...)}), in which case it is
 * skipped with an SKIPPED marker comment and a compiler note is emitted.</p>
 */
class AsPropertyExpander {

    /** Fully qualified name of the {@code @AsProperty} annotation. */
    private static final String AS_PROPERTY_FQN = "org.strata.jverify.AsProperty";

    private final TreeMaker maker;
    private final Names names;
    private final Messager messager;
    private final ClauseTranslator clauseTranslator;

    AsPropertyExpander(Context context, ProcessingEnvironment processingEnv) {
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.messager = processingEnv.getMessager();
        this.clauseTranslator = new ClauseTranslator(messager);
    }

    /**
     * Append a synthesized {@code @Property} method for every {@code @AsProperty}
     * method found in {@code classDecl}. Inner/nested classes are handled recursively.
     */
    void expandClass(JCTree.JCClassDecl classDecl) {
        ListBuffer<JCTree> newMembers = new ListBuffer<>();
        for (JCTree member : classDecl.defs) {
            newMembers.append(member);
            if (member instanceof JCTree.JCMethodDecl methodDecl && hasAsProperty(methodDecl)) {
                JCTree.JCMethodDecl propertyMethod = synthesizePropertyMethod(methodDecl);
                if (propertyMethod != null) {
                    newMembers.append(propertyMethod);
                }
            }
            if (member instanceof JCTree.JCClassDecl nested) {
                expandClass(nested);
            }
        }
        classDecl.defs = newMembers.toList();
    }

    private boolean hasAsProperty(JCTree.JCMethodDecl methodDecl) {
        if (methodDecl.mods == null || methodDecl.mods.annotations == null) {
            return false;
        }
        for (JCTree.JCAnnotation ann : methodDecl.mods.annotations) {
            String name = ann.annotationType.toString();
            if (name.equals("AsProperty") || name.equals(AS_PROPERTY_FQN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the synthesized {@code @Property} method.
     * Returns null if the method cannot be translated (e.g. no postcondition).
     */
    private JCTree.JCMethodDecl synthesizePropertyMethod(JCTree.JCMethodDecl original) {
        if (original.body == null) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                "@AsProperty method '" + original.name + "' has no body, skipping");
            return null;
        }

        ContractClauses clauses = ContractClauses.extract(original.body);

        if (clauses.postconditions.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                "@AsProperty method '" + original.name + "' has no postcondition, skipping");
            return null;
        }

        TreeCopier<Void> copier = new TreeCopier<>(maker);

        // 1. Parameters: copy from the original method, preserving @ForAll annotations.
        ListBuffer<JCTree.JCVariableDecl> propParams = new ListBuffer<>();
        for (JCTree.JCVariableDecl param : original.params) {
            propParams.append(copier.copy(param));
        }

        // 2. Body statements:
        //    a. Assume.that(P) for each precondition
        //    b. var ret = method(args);   (only if the postcondition has a return binding)
        //    c. return <postcondition body>;
        ListBuffer<JCTree.JCStatement> stmts = new ListBuffer<>();

        for (ClauseTranslator.Clause pre : clauses.preconditions) {
            JCTree.JCStatement assumeStmt = clauseTranslator.translatePrecondition(pre, maker, names, copier);
            if (assumeStmt != null) {
                stmts.append(assumeStmt);
            }
        }

        ClauseTranslator.PostconditionResult post =
            clauseTranslator.translatePostconditions(clauses.postconditions, original, maker, names, copier);
        if (post == null) {
            return null;
        }
        if (post.callBinding != null) {
            stmts.append(post.callBinding);
        }
        stmts.append(post.returnStatement);

        JCTree.JCBlock body = maker.Block(0, stmts.toList());

        // 3. Annotations: replace @AsProperty with @Property, keep everything else.
        ListBuffer<JCTree.JCAnnotation> propAnnotations = new ListBuffer<>();
        propAnnotations.append(makePropertyAnnotation());

        JCTree.JCModifiers propMods = maker.Modifiers(0, propAnnotations.toList());

        // 4. Method name: append "Property" (e.g. binarySearch -> binarySearchProperty)
        Name newName = names.fromString(original.name.toString() + "Property");

        // 5. Return type: boolean (jqwik @Property returns a boolean assertion result).
        JCTree.JCExpression returnType = maker.TypeIdent(TypeTag.BOOLEAN);

        return maker.MethodDef(
                propMods,
                newName,
                returnType,
                List.nil(),                          // type parameters
                null,                                // receiver
                propParams.toList(),
                List.nil(),                          // thrown
                body,
                null                                 // default value
        );
    }

    /**
     * Build {@code @net.jqwik.api.Property} annotation.
     */
    private JCTree.JCAnnotation makePropertyAnnotation() {
        JCTree.JCExpression typeRef = qualifiedIdent("net", "jqwik", "api", "Property");
        return maker.Annotation(typeRef, List.nil());
    }

    private JCTree.JCExpression qualifiedIdent(String... parts) {
        JCTree.JCExpression e = maker.Ident(names.fromString(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            e = maker.Select(e, names.fromString(parts[i]));
        }
        return e;
    }
}
