package org.strata.jverify.contracts2jqwik;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure source-to-source translator: takes the contents of a Java file containing
 * {@code @AsProperty}-annotated methods and produces a new Java compilation unit
 * containing the corresponding jqwik {@code @Property} methods.
 *
 * <p>Translation rules:</p>
 * <ul>
 *     <li>{@code precondition(P)} → {@code Assume.that(P);}</li>
 *     <li>{@code postcondition((T r) -> Q)} →
 *         {@code T r = method(args); return Q;}</li>
 *     <li>{@code postcondition(boolExpr)} → {@code method(args); return boolExpr;}</li>
 *     <li>Multiple postconditions are conjoined with {@code &&} in source order.
 *         All lambda postconditions in a method must agree on the binding name.</li>
 *     <li>{@code old(e)} is made executable by hoisting {@code e} into a
 *         pre-state temporary evaluated before the method call and replacing
 *         the {@code old(e)} reference with that temporary. This works when
 *         {@code e} refers only to the contract method's parameters; if it
 *         refers to a local declared inside the contract body, the clause is
 *         skipped with a warning.</li>
 *     <li>Frame clauses {@code reads(...)} and {@code modifies(...)} constrain
 *         the verifier, not runtime behaviour, and are dropped.</li>
 *     <li>Quantifiers ({@code forall}, {@code exists}) are non-executable;
 *         clauses containing them are skipped with a warning.</li>
 * </ul>
 *
 * <p>The output is a class {@code <Original>Generated} that {@code extends}
 * the original, so it can call the {@code @AsProperty} methods directly.
 * This avoids modifying the input file and produces something a human can
 * read and code-review.</p>
 */
public final class Contracts2Jqwik {

    /** Name of the JVerify precondition method. */
    private static final String PRECONDITION = "precondition";
    /** Name of the JVerify postcondition method. */
    private static final String POSTCONDITION = "postcondition";
    /** Simple-name match for {@code @AsProperty}. */
    private static final String AS_PROPERTY_SIMPLE = "AsProperty";
    /** Fully qualified name for {@code @AsProperty}. */
    private static final String AS_PROPERTY_FQN = "org.strata.jverify.AsProperty";

    /** Result of translating a single input file. */
    public static final class Result {
        /** The generated Java source, or null if there were no {@code @AsProperty} methods. */
        public final String output;
        /** A reasonable filename for the generated source (e.g. {@code FooGenerated.java}). */
        public final String suggestedFileName;
        /** Soft warnings about clauses that were skipped or could not be translated. */
        public final List<String> warnings;

        Result(String output, String suggestedFileName, List<String> warnings) {
            this.output = output;
            this.suggestedFileName = suggestedFileName;
            this.warnings = warnings;
        }
    }

    private Contracts2Jqwik() {}

    /**
     * Translate the contents of a Java source file. {@code inputFileName} is
     * used only to construct the suggested output filename and for diagnostics.
     */
    public static Result translate(String source, String inputFileName) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        List<String> warnings = new ArrayList<>();

        // Find the top-level public class. We treat the first top-level type as
        // the one to subclass; nested classes are not handled in this version.
        Optional<ClassOrInterfaceDeclaration> originalOpt = cu.getTypes().stream()
            .filter(t -> t instanceof ClassOrInterfaceDeclaration)
            .map(t -> (ClassOrInterfaceDeclaration) t)
            .findFirst();
        if (originalOpt.isEmpty()) {
            return new Result(null, null, warnings);
        }
        ClassOrInterfaceDeclaration original = originalOpt.get();

        List<MethodDeclaration> asPropertyMethods = original.getMethods().stream()
            .filter(Contracts2Jqwik::hasAsProperty)
            .toList();
        if (asPropertyMethods.isEmpty()) {
            return new Result(null, null, warnings);
        }

        // Build the output compilation unit.
        CompilationUnit out = new CompilationUnit();
        cu.getPackageDeclaration().ifPresent(pkg ->
            out.setPackageDeclaration(pkg.getNameAsString()));

        // Imports needed by the generated code.
        out.addImport("net.jqwik.api.Assume");
        out.addImport("net.jqwik.api.ForAll");
        out.addImport("net.jqwik.api.Property");
        // Carry the original file's imports forward — the contract bodies almost
        // certainly use them (e.g. JVerify static imports, sequence helpers).
        cu.getImports().forEach(out::addImport);

        String generatedClassName = original.getNameAsString() + "Generated";
        ClassOrInterfaceDeclaration generated = out.addClass(generatedClassName);
        generated.addExtendedType(original.getNameAsString());
        generated.addOrphanComment(new com.github.javaparser.ast.comments.LineComment(
            " Generated from " + inputFileName + " by contracts2jqwik. Do not edit by hand;"));
        generated.addOrphanComment(new com.github.javaparser.ast.comments.LineComment(
            " regenerate by re-running contracts2jqwik on the source file."));

        for (MethodDeclaration originalMethod : asPropertyMethods) {
            MethodDeclaration property = synthesizeProperty(originalMethod, warnings);
            if (property != null) {
                generated.addMember(property);
            }
        }

        if (generated.getMembers().isEmpty()) {
            warnings.add("no translatable @AsProperty methods in " + inputFileName);
            return new Result(null, generatedClassName + ".java", warnings);
        }

        return new Result(out.toString(), generatedClassName + ".java", warnings);
    }

    /** Returns true if {@code method} is annotated with {@code @AsProperty}. */
    private static boolean hasAsProperty(MethodDeclaration method) {
        for (AnnotationExpr ann : method.getAnnotations()) {
            String name = ann.getNameAsString();
            if (name.equals(AS_PROPERTY_SIMPLE) || name.equals(AS_PROPERTY_FQN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the synthesized {@code @Property} method for one {@code @AsProperty}
     * input. Returns null if translation failed (e.g. no postcondition).
     */
    private static MethodDeclaration synthesizeProperty(MethodDeclaration original, List<String> warnings) {
        Optional<BlockStmt> bodyOpt = original.getBody();
        if (bodyOpt.isEmpty()) {
            warnings.add("@AsProperty method '" + original.getNameAsString() + "' has no body");
            return null;
        }
        BlockStmt body = bodyOpt.get();

        List<Expression> preconditions = new ArrayList<>();
        List<Expression> postconditions = new ArrayList<>();
        for (Statement stmt : body.getStatements()) {
            extractClause(stmt, PRECONDITION).ifPresent(preconditions::add);
            extractClause(stmt, POSTCONDITION).ifPresent(postconditions::add);
        }

        if (postconditions.isEmpty()) {
            warnings.add("@AsProperty method '" + original.getNameAsString()
                + "' has no postcondition; skipping");
            return null;
        }

        // Translate postconditions: collect bodies and find the (single) binding name.
        List<Expression> postBodies = new ArrayList<>();
        String returnVarName = null;
        for (Expression post : postconditions) {
            TranslatedPost t = translatePostcondition(post, original, warnings);
            if (t == null) continue;
            if (t.bindingName != null) {
                if (returnVarName == null) {
                    returnVarName = t.bindingName;
                } else if (!returnVarName.equals(t.bindingName)) {
                    warnings.add("postconditions in '" + original.getNameAsString()
                        + "' use different return-binding names ('" + returnVarName
                        + "' vs '" + t.bindingName + "'); skipping");
                    return null;
                }
            }
            postBodies.add(t.body);
        }
        if (postBodies.isEmpty()) {
            warnings.add("@AsProperty method '" + original.getNameAsString()
                + "' has no executable postcondition; skipping");
            return null;
        }

        // Build the generated method.
        MethodDeclaration property = new MethodDeclaration();
        property.addAnnotation(new MarkerAnnotationExpr("Property"));
        property.setModifier(Modifier.Keyword.PUBLIC, true);
        property.setType("boolean");
        property.setName(original.getNameAsString() + "Property");

        // Parameters: copy from original, preserving their @ForAll annotations.
        for (Parameter p : original.getParameters()) {
            property.addParameter(p.clone());
        }

        BlockStmt newBody = new BlockStmt();

        // 1. Preconditions: Assume.that(P);
        for (Expression pre : preconditions) {
            if (isNonExecutable(pre)) {
                warnings.add("skipping non-executable precondition in '"
                    + original.getNameAsString() + "': " + pre);
                continue;
            }
            MethodCallExpr assumeCall = new MethodCallExpr(
                new NameExpr("Assume"), "that", NodeList.nodeList(pre.clone()));
            newBody.addStatement(new ExpressionStmt(assumeCall));
        }

        // 2. Pre-state capture for old(...). Each distinct old(e) occurrence
        //    across all postcondition bodies is hoisted into a temp evaluated
        //    BEFORE the method call, and the old(e) node is replaced with a
        //    reference to that temp. This makes a clause like
        //    `r.length() == old(this.length()) + 1` executable: we snapshot
        //    `this.length()` pre-call, run the method, then compare.
        OldHoister hoister = new OldHoister();
        for (Expression postBody : postBodies) {
            hoister.hoist(postBody);
        }
        for (OldHoister.Capture c : hoister.captures) {
            // var __oldN = <expr captured in pre-state>;
            newBody.addStatement(new ExpressionStmt(
                new com.github.javaparser.ast.expr.VariableDeclarationExpr(
                    new com.github.javaparser.ast.body.VariableDeclarator(
                        new com.github.javaparser.ast.type.VarType(),
                        c.tempName,
                        c.capturedExpr))));
        }

        // 3. Call binding: T r = method(args);  or  method(args);
        MethodCallExpr call = new MethodCallExpr(null, original.getNameAsString());
        for (Parameter p : original.getParameters()) {
            call.addArgument(new NameExpr(p.getNameAsString()));
        }
        if (returnVarName != null) {
            // T r = method(args);
            Type t = original.getType();
            newBody.addStatement(new com.github.javaparser.ast.stmt.ExpressionStmt(
                new com.github.javaparser.ast.expr.VariableDeclarationExpr(
                    new com.github.javaparser.ast.body.VariableDeclarator(
                        t.clone(), returnVarName, call))));
        } else {
            // method(args);  (we still call it so any side effects of the impl
            // run; the result is discarded since no clause referenced it)
            newBody.addStatement(new ExpressionStmt(call));
        }

        // 4. Return conjunction.
        // Each clause body is wrapped in EnclosedExpr to preserve precedence:
        // a clause like `r == x || r == -x` must become `(r == x || r == -x)`
        // before being folded with `&&`, otherwise Java's || having lower
        // precedence than && silently changes the meaning of the conjunction.
        Expression conjunction = new com.github.javaparser.ast.expr.EnclosedExpr(postBodies.get(0));
        for (int i = 1; i < postBodies.size(); i++) {
            Expression rhs = new com.github.javaparser.ast.expr.EnclosedExpr(postBodies.get(i));
            conjunction = new BinaryExpr(conjunction, rhs, BinaryExpr.Operator.AND);
        }
        newBody.addStatement(new ReturnStmt(conjunction));

        property.setBody(newBody);
        return property;
    }

    /**
     * Walks postcondition expressions and replaces each {@code old(e)} call
     * with a reference to a pre-state temporary, recording the captured inner
     * expression {@code e} so the caller can emit
     * {@code var __oldN = e;} before the method call.
     *
     * <p>Identical {@code old(e)} expressions (by source text) share a single
     * temporary, so {@code old(this.size())} appearing in two clauses captures
     * once.</p>
     */
    private static final class OldHoister {
        /** A captured pre-state expression and the temp name it binds to. */
        static final class Capture {
            final String tempName;
            final Expression capturedExpr;
            Capture(String tempName, Expression capturedExpr) {
                this.tempName = tempName;
                this.capturedExpr = capturedExpr;
            }
        }

        final List<Capture> captures = new ArrayList<>();
        private final java.util.Map<String, String> exprTextToTemp = new java.util.HashMap<>();
        private int counter = 0;

        /**
         * Replace every {@code old(e)} in {@code expr} (in place) with a
         * reference to its pre-state temporary.
         */
        void hoist(Expression expr) {
            // Collect old(...) calls first to avoid mutating during traversal.
            List<MethodCallExpr> oldCalls = expr.findAll(MethodCallExpr.class).stream()
                .filter(c -> c.getNameAsString().equals("old") && c.getArguments().size() == 1)
                .toList();
            for (MethodCallExpr oldCall : oldCalls) {
                Expression inner = oldCall.getArgument(0);
                String key = inner.toString();
                String temp = exprTextToTemp.get(key);
                if (temp == null) {
                    temp = "__old" + (counter++);
                    exprTextToTemp.put(key, temp);
                    captures.add(new Capture(temp, inner.clone()));
                }
                oldCall.replace(new NameExpr(temp));
            }
        }
    }

    /**
     * If {@code stmt} is a top-level call to {@code methodName(arg)}, return the
     * argument expression. Otherwise empty.
     */
    private static Optional<Expression> extractClause(Statement stmt, String methodName) {
        if (!(stmt instanceof ExpressionStmt exprStmt)) return Optional.empty();
        Expression e = exprStmt.getExpression();
        if (!(e instanceof MethodCallExpr call)) return Optional.empty();
        if (!call.getNameAsString().equals(methodName)) return Optional.empty();
        if (call.getArguments().isEmpty()) return Optional.empty();
        return Optional.of(call.getArgument(0));
    }

    /** Postcondition translation result: the body to assert, plus the binding name (if any). */
    private static final class TranslatedPost {
        final Expression body;
        final String bindingName;

        TranslatedPost(Expression body, String bindingName) {
            this.body = body;
            this.bindingName = bindingName;
        }
    }

    private static TranslatedPost translatePostcondition(Expression arg,
                                                         MethodDeclaration original,
                                                         List<String> warnings) {
        if (arg instanceof LambdaExpr lambda) {
            if (lambda.getParameters().size() != 1) {
                warnings.add("postcondition lambda in '" + original.getNameAsString()
                    + "' must have exactly one parameter; got " + lambda.getParameters().size());
                return null;
            }
            String binding = lambda.getParameter(0).getNameAsString();
            Expression body;
            if (lambda.getExpressionBody().isPresent()) {
                body = lambda.getExpressionBody().get();
            } else if (lambda.getBody() instanceof BlockStmt block && block.getStatements().size() == 1
                       && block.getStatement(0) instanceof ReturnStmt ret
                       && ret.getExpression().isPresent()) {
                body = ret.getExpression().get();
            } else {
                warnings.add("postcondition lambda in '" + original.getNameAsString()
                    + "' must have an expression body or a single return statement");
                return null;
            }
            if (isNonExecutable(body)) {
                warnings.add("skipping non-executable postcondition in '"
                    + original.getNameAsString() + "': " + body);
                return null;
            }
            if (hasOutOfScopeOld(body, original, binding, warnings)) {
                return null;
            }
            return new TranslatedPost(body.clone(), binding);
        }
        // postcondition(boolExpr) — no return binding for this clause.
        if (isNonExecutable(arg)) {
            warnings.add("skipping non-executable postcondition in '"
                + original.getNameAsString() + "': " + arg);
            return null;
        }
        if (hasOutOfScopeOld(arg, original, null, warnings)) {
            return null;
        }
        return new TranslatedPost(arg.clone(), null);
    }

    /**
     * Returns true (and emits a warning) if any {@code old(e)} in {@code body}
     * references a name that won't be in scope in the generated property
     * method. The generated method only has the contract method's parameters
     * (and the postcondition's result binding) in scope — NOT any locals
     * declared inside the contract body (e.g. a receiver constructed there).
     *
     * <p>A pre-state capture {@code var __oldN = e;} is emitted before the call,
     * so {@code e} may reference only the property method's parameters. If
     * {@code old(e)} references a contract-body local, the capture would be
     * ill-formed; we skip the clause rather than emit broken code. Handling
     * {@code old()} over a constructed receiver is Phase 2 work (receiver
     * generation).</p>
     */
    private static boolean hasOutOfScopeOld(Expression body,
                                            MethodDeclaration original,
                                            String resultBinding,
                                            List<String> warnings) {
        java.util.Set<String> inScope = new java.util.HashSet<>();
        for (Parameter p : original.getParameters()) {
            inScope.add(p.getNameAsString());
        }
        if (resultBinding != null) {
            inScope.add(resultBinding);
        }
        for (MethodCallExpr oldCall : body.findAll(MethodCallExpr.class)) {
            if (!oldCall.getNameAsString().equals("old") || oldCall.getArguments().size() != 1) {
                continue;
            }
            Expression inner = oldCall.getArgument(0);
            for (NameExpr name : inner.findAll(NameExpr.class)) {
                if (!inScope.contains(name.getNameAsString())) {
                    warnings.add("skipping postcondition in '" + original.getNameAsString()
                        + "': old(...) references '" + name.getNameAsString()
                        + "' which is not a parameter of the contract method and so is not in "
                        + "scope in the generated test. old() over a constructed receiver needs "
                        + "Phase 2 receiver generation.");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A clause is non-executable if it contains a quantifier
     * ({@code forall} / {@code exists}), since those range over infinite
     * domains and have no runtime semantics.
     *
     * <p>{@code old(...)} is NOT non-executable: it is handled by hoisting the
     * inner expression into a pre-call temporary (see {@link OldHoister}),
     * so a postcondition like {@code r.length() == old(this.length()) + 1}
     * becomes a real, jqwik-checkable assertion.</p>
     */
    private static boolean isNonExecutable(Expression expr) {
        return expr.findAll(MethodCallExpr.class).stream()
            .map(MethodCallExpr::getNameAsString)
            .anyMatch(n -> n.equals("forall") || n.equals("exists"));
    }
}
