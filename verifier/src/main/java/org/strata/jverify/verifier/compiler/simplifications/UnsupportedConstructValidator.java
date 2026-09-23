package org.strata.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeKind;
import org.strata.jverify.Impure;
import org.strata.jverify.common.Common;
import org.strata.jverify.verifier.compiler.Reporter;

/**
 * Reports the Java constructs JVerify does not support, as errors, before Laurel
 * generation runs.
 *
 * <p>These checks used to live in the Dafny back-end's generator, which walked every
 * class member and every expression on its way to Dafny and so had a natural place to
 * refuse one. The Laurel generator replaced that walk with a per-method refusal policy
 * ({@code RefusalGates}), which demotes an untranslatable method to a warning instead of
 * rejecting the program. That is the right answer for a construct Laurel merely cannot
 * express yet, but the wrong one for a construct whose translation would be UNSOUND, or
 * which the user can only have written by mistake: those need to stop the build with a
 * message naming the construct, not a warning naming the method.
 *
 * <p>Kept out of the generator deliberately: what a program is allowed to contain does
 * not depend on how it is encoded, so these are back-end agnostic and belong with the
 * other {@code simplifications} passes that already report this way. Runs before
 * javac's {@code Lower}, because the shapes it inspects (initializer blocks) are
 * desugared away there.
 */
public class UnsupportedConstructValidator extends TreeScanner {

    private final Reporter reporter;
    private final Symtab symtab;
    private final Types types;
    private final JVerifyUtils jverifyUtils;

    public UnsupportedConstructValidator(Context context) {
        this.reporter = Reporter.instance(context);
        this.symtab = Symtab.instance(context);
        this.types = Types.instance(context);
        this.jverifyUtils = JVerifyUtils.instance(context);
    }

    /** Validate one compilation unit. Contract sources are the caller's to exclude. */
    public void validate(JCTree.JCCompilationUnit unit) {
        reporter.compilationUnit = unit;
        scan(unit);
    }

    /**
     * Whether each enclosing switch is an EXPRESSION, innermost last. A case belongs to
     * the switch immediately around it, so the kind of that one is what decides -- a
     * plain depth count said only "somewhere inside an expression", which misfired on a
     * switch STATEMENT nested in an expression's arm block.
     */
    private final java.util.Deque<Boolean> enclosingSwitchIsExpression = new java.util.ArrayDeque<>();

    @Override
    public void visitSwitchExpression(JCTree.JCSwitchExpression tree) {
        enclosingSwitchIsExpression.push(true);
        try {
            super.visitSwitchExpression(tree);
        } finally {
            enclosingSwitchIsExpression.pop();
        }
    }

    @Override
    public void visitSwitch(JCTree.JCSwitch tree) {
        enclosingSwitchIsExpression.push(false);
        try {
            super.visitSwitch(tree);
        } finally {
            enclosingSwitchIsExpression.pop();
        }
    }

    /**
     * A {@code throw} as the body of a switch-expression case rule
     * ({@code case 0 -> throw ...}). A switch expression must produce a value on every
     * arm, and a throwing arm produces none, so there is nothing to translate.
     *
     * <p>Reported here, at the {@code throw}, rather than left to the Laurel generator,
     * whose refusal is a whole-method "Unsupported expression: JCSwitchExpression" that
     * names neither the offending arm nor its position. Restricted to switch
     * EXPRESSIONS: the same arm shape in a switch STATEMENT is an ordinary throw, so the
     * test is on the IMMEDIATELY enclosing switch, not on being anywhere inside one.
     */
    @Override
    public void visitCase(JCTree.JCCase tree) {
        super.visitCase(tree);
        if (Boolean.TRUE.equals(enclosingSwitchIsExpression.peek())
                && tree.stats != null && tree.stats.size() == 1
                && tree.stats.getFirst() instanceof JCTree.JCThrow thrown) {
            error(thrown, "notSupported", "switch rule throw statement");
        }
    }

    /**
     * An instance or static initializer block. Both run outside any method JVerify can
     * attach a contract to, so their effects would silently escape verification.
     *
     * <p>Empty blocks are ignored: javac's own lowering synthesizes them.
     */
    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        var outer = currentOwner;
        if (tree.sym != null) {
            currentOwner = tree.sym;
        }
        for (JCTree def : tree.defs) {
            if (def instanceof JCTree.JCBlock block && !block.stats.isEmpty()) {
                error(block, "notSupported", "an initializer block");
            }
        }
        checkRecordMembers(tree);
        super.visitClassDef(tree);
        currentOwner = outer;
    }

    /**
     * The innermost class or method being scanned, used to decide whether the code here
     * is opted out of verification. Re-evaluated per member rather than pruned at the
     * class, so a member that re-opts in with {@code @Verify(true)} under a non-sticky
     * class-level opt-out still gets its diagnostics.
     */
    private com.sun.tools.javac.code.Symbol currentOwner;

    /**
     * Every diagnostic this pass reports goes through here, so opted-out code cannot
     * produce one. This pass CANNOT rely on opted-out code having been removed:
     * VerifyAnnotationCompiler strips METHOD BODIES only, while initializer blocks,
     * record members and field-initializer expressions all survive it.
     */
    private void error(Object position, String key, Object... args) {
        if (currentOwner != null && VerifyAnnotationCompiler.isOptedOutOfVerification(
                currentOwner, jverifyUtils.getVerifyClassSymbol())) {
            return;
        }
        if (position instanceof JCTree tree) {
            reporter.reportError(tree, key, args);
        } else {
            reporter.reportError((JCDiagnostic.DiagnosticPosition) position, key, args);
        }
    }

    /**
     * The members a record may not declare. A record translates to a Laurel composite
     * whose identity IS its components, and JVerify derives the constructor, the
     * accessors, and equality from them. A hand-written version of any of those could
     * disagree with what was derived, so the two would silently diverge.
     *
     * <p>Only user-written members are checked: javac puts the canonical constructor and
     * the component accessors it generates into the same member list, and flagging those
     * would reject every record.
     */
    private void checkRecordMembers(JCTree.JCClassDecl tree) {
        if (tree.sym == null || !tree.sym.isRecord()) {
            return;
        }
        // @Impure is about heap identity, which a value-like composite does not have.
        if (jverifyUtils.isAnnotatedRecursive(tree.type, Impure.class)) {
            error(tree, "modifiableForbidden", "a record class");
        }
        Set<String> componentNames = new LinkedHashSet<>();
        for (var component : tree.sym.getRecordComponents()) {
            componentNames.add(component.name.toString());
        }
        for (JCTree def : tree.defs) {
            if (!(def instanceof JCTree.JCMethodDecl method) || isGenerated(method)) {
                continue;
            }
            String name = method.name.toString();
            var params = method.getParameters();
            if (TreeInfo.isConstructor(method)) {
                error(method, "notSupported",
                        "verified explicit record constructor");
            } else if (params.isEmpty() && componentNames.contains(name)) {
                error(method, "notSupported",
                        "explicit record component accessor method");
            } else if (name.equals("equals") && params.size() == 1
                    && params.getFirst().type != null
                    && params.getFirst().type.tsym == symtab.objectType.tsym) {
                error(method, "notSupported",
                        "overridden equals method in record");
            } else if (name.equals("hashCode") && params.isEmpty()) {
                error(method, "notSupported",
                        "overridden hashCode method in record");
            }
        }
    }

    /** Whether javac, not the user, wrote this member. */
    private static boolean isGenerated(JCTree.JCMethodDecl method) {
        long flags = method.sym != null ? method.sym.flags() : method.mods.flags;
        return (flags & (Flags.SYNTHETIC | Flags.GENERATEDCONSTR | Flags.MANDATED)) != 0;
    }

    /** Whether the method currently being scanned is a constructor. */
    private boolean inConstructor;

    /** Whether the scan is inside a lifted contract header, where clause calls belong. */
    private boolean inContractHeader;

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        boolean outer = inConstructor;
        var outerOwner = currentOwner;
        inConstructor = tree.sym != null && JVerifyUtils.isConstructor(tree.sym);
        if (tree.sym != null) {
            currentOwner = tree.sym;
        }
        super.visitMethodDef(tree);
        inConstructor = outer;
        currentOwner = outerOwner;
    }

    /**
     * Track lifted contract headers. {@link MethodOrLoopContractCompiler} rewrites a
     * contract-bearing body -- of a METHOD or of a LOOP -- into a block whose first
     * statement is a block holding the contract calls.
     *
     * <p>The header is still SCANNED: a clause is as capable of containing an unsupported
     * operator as any other expression, and an early version that skipped it outright
     * missed two thirds of them. Only the misplaced-contract-call check is disabled
     * inside it, since that is the one place those calls are correct.
     */
    @Override
    public void visitBlock(JCTree.JCBlock tree) {
        if (MethodOrLoopContractCompiler.hasContractStructure(tree)) {
            boolean outer = inContractHeader;
            inContractHeader = true;
            scan(tree.getStatements().head);
            inContractHeader = outer;
            scan(tree.getStatements().tail);
        } else {
            super.visitBlock(tree);
        }
    }

    /**
     * A contract call ({@code precondition}, {@code postcondition}, {@code invariant},
     * ...) reached outside any contract header. The leading run of such calls is lifted
     * out of the body, so one left behind sits after a real statement -- where the user
     * plainly meant it to apply, yet nothing reads it and the obligation silently
     * vanishes.
     *
     * <p>Constructors get their own message: their contract may also follow the {@code
     * super}/{@code this} call, so "before the rest of the body" would be wrong advice.
     */
    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
        if (!inContractHeader && isHeaderContractCall(tree)) {
            error(tree, inConstructor ? "contractForConstructor" : "contractAfterBody");
        }
        checkIsAbstractPlacement(tree);
        checkQuantifierArgument(tree);
    }

    /**
     * A {@code forall}/{@code exists} argument that is not a lambda written in place.
     * The quantifier needs the binder to bind, so a variable holding a functional
     * interface cannot be translated. Reported HERE, at the argument, rather than
     * left to the Laurel generator, whose refusal demotes the whole method and so
     * names neither the construct nor its position.
     */
    private void checkQuantifierArgument(JCTree.JCMethodInvocation tree) {
        var jverifyMethod = JVerifyUtils.getJVerifyMethod(tree);
        if (jverifyMethod == null
                || !jverifyMethod.owner.getQualifiedName().contentEquals(JVerifyUtils.JVERIFY_CLASS)) {
            return;
        }
        String name = jverifyMethod.getQualifiedName().toString();
        if (!name.equals("forall") && !name.equals("exists")) {
            return;
        }
        if (tree.args.size() == 1 && tree.args.getFirst() instanceof JCTree.JCLambda) {
            return;
        }
        error(tree.args.size() == 1 ? tree.args.getFirst() : tree,
                "argumentMustBeLambda", name);
    }

    /**
     * {@code isAbstract()} outside a precondition. Only a precondition can be abstract --
     * it names "whatever the overridden method requires" -- so the call has no meaning in
     * a postcondition, {@code decreases}, {@code reads}, or {@code modifies}.
     *
     * <p>Detected by survival rather than by inspecting the enclosing clause: {@link
     * IsAbstractCompiler} runs earlier in the pipeline and rewrites the precondition uses
     * into a {@code thePreconditionOf$} call, so any of these still standing is by
     * construction somewhere it does not belong.
     *
     * <p>Reported at the method select, not the invocation, to put the caret on the name.
     */
    private void checkIsAbstractPlacement(JCTree.JCMethodInvocation tree) {
        var jverifyMethod = JVerifyUtils.getJVerifyMethod(tree);
        if (jverifyMethod == null
                || !jverifyMethod.owner.getQualifiedName().contentEquals(JVerifyUtils.JVERIFY_CLASS)) {
            return;
        }
        String name = jverifyMethod.getQualifiedName().toString();
        if (name.equals("isAbstract") || name.equals("isAbstractBoolean")) {
            error(tree.getMethodSelect(), "notSupported",
                    name + "() in clauses other than a precondition");
        }
    }

    /**
     * Whether this call is one of the contract methods that may only appear in a contract
     * header. {@code check} and {@code assume} are excluded: they are ordinary statements
     * and belong in the body.
     *
     * <p>Only methods declared on {@code JVerify} ITSELF count. The same clause names also
     * exist on the {@code ThrowsOnCase} handle {@code throwsOn} returns, and a misused
     * chain there ({@code throwsOn} included) already gets a message naming the specific
     * misuse -- a captured or detached handle -- which this one would only duplicate with
     * something vaguer.
     */
    private static boolean isHeaderContractCall(JCTree.JCMethodInvocation invocation) {
        var jverifyMethod = JVerifyUtils.getJVerifyMethod(invocation);
        if (jverifyMethod == null
                || !jverifyMethod.owner.getQualifiedName().contentEquals(JVerifyUtils.JVERIFY_CLASS)) {
            return false;
        }
        return switch (jverifyMethod.getQualifiedName().toString()) {
            case Common.PRECONDITION, "postcondition", "invariant", "decreases", "reads",
                 "modifies" -> true;
            default -> false;
        };
    }

    /**
     * Operators with no Laurel counterpart, and the one whose counterpart would be wrong.
     *
     * <p>Checked in the same order the Dafny generator used, because the cases overlap:
     * {@code %} on a floating-point operand is rejected before the equality rule, and the
     * equality rule before the bitwise rule, so the most specific message wins.
     */
    @Override
    public void visitBinary(JCTree.JCBinary tree) {
        super.visitBinary(tree);
        if (checkArithmetic(tree)) {
            return;
        }
        String opName = tree.getOperator().name.toString();
        if ((opName.equals("==") || opName.equals("!="))
                && tree.lhs.type != null && tree.rhs.type != null
                && !equalityIsSound(tree.lhs.type, tree.rhs.type)) {
            error(tree, "equalityOperatorRestricted", opName);
        }
    }

    /**
     * The compound forms ({@code &=}, {@code %=}, ...) carry the same operator symbol as
     * their binary counterpart and are just as untranslatable, but they are {@link
     * JCTree.JCAssignOp} rather than {@link JCTree.JCBinary} -- so checking only the
     * latter misses two thirds of the occurrences in a fixture that exercises both forms.
     */
    @Override
    public void visitAssignop(JCTree.JCAssignOp tree) {
        super.visitAssignop(tree);
        checkArithmetic(tree);
    }

    /**
     * The operator rules shared by the plain and compound forms. Returns whether an error
     * was reported, so a caller can stop before applying a less specific rule.
     */
    private boolean checkArithmetic(JCTree.JCOperatorExpression tree) {
        String opName = tree.getOperator().name.toString();
        // Laurel has no floating-point remainder, and integer % is a different function,
        // so this cannot be quietly retargeted. The RESOLVED operator's operand types are
        // the authority on whether this is a floating-point remainder: binary numeric
        // promotion has already been applied, so `3 % 2.0`, `2.0 % 3` and the compound
        // `intVar %= 2.0` (whose operator is double modulo before the narrowing back to
        // int) all resolve to the double `%` operator -- which inspecting only the left
        // operand's declared type missed.
        if (opName.equals("%") && operatorActsOnFloatingPoint(tree)) {
            error(tree, "notSupported",
                    "modulo operator (%) with floating-point types");
            return true;
        }
        if (isBitwise(opName)) {
            error(tree, "notSupported", "operator " + tree.getOperator());
            return true;
        }
        return false;
    }

    /**
     * Unary operators with no Laurel counterpart -- in practice {@code ~}. Negation, plus,
     * logical not, and the increment/decrement forms are all translated, so only the
     * remaining tags reach here.
     */
    @Override
    public void visitUnary(JCTree.JCUnary tree) {
        super.visitUnary(tree);
        switch (tree.getTag()) {
            case POSTINC, PREINC, POSTDEC, PREDEC, NOT, NEG, POS -> { }
            default -> error(tree, "notSupported", "operator " + tree.getOperator());
        }
    }

    /** Bit-level operators: Laurel models integers as unbounded, so these have no meaning. */
    private static boolean isBitwise(String opName) {
        return switch (opName) {
            case "&", "|", "^", "<<", ">>", ">>>" -> true;
            default -> false;
        };
    }

    /**
     * True when the resolved operator computes over floating-point operands. The
     * operator symbol's own parameter types reflect the promotion Java applied, so
     * this catches a mixed {@code int % double} and a compound {@code int %= double}
     * alike, where inspecting a declared operand type would not.
     */
    private static boolean operatorActsOnFloatingPoint(JCTree.JCOperatorExpression tree) {
        var operator = tree.getOperator();
        if (operator == null || operator.type == null) {
            return false;
        }
        for (Type param : operator.type.getParameterTypes()) {
            if (param != null && isFloatingPoint(param)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFloatingPoint(Type type) {
        return switch (type.getTag()) {
            case FLOAT, DOUBLE -> true;
            default -> false;
        };
    }

    /**
     * Whether {@code ==} on these operand types keeps Java's meaning once translated.
     *
     * <p>Java compares references; some Java reference types translate to Laurel VALUES
     * (a record becomes a composite, a {@code String} a Laurel string). Two distinct
     * objects of such a type are unequal under Java's {@code ==} yet structurally equal
     * after translation, so accepting the comparison would prove things Java does not
     * guarantee. Sound only when at least one operand cannot be one of those types:
     *
     * <ul>
     *   <li>either side is the {@code null} literal -- comparing a value-translated type
     *       against {@code null} is rejected downstream, so it cannot slip through;</li>
     *   <li>either side is primitive -- then the other is primitive or boxed and unboxing
     *       promotion applies, which translation preserves;</li>
     *   <li>either side is definitely not value-translated -- being a reference type on
     *       both sides, the comparison stays a reference comparison.</li>
     * </ul>
     */
    private boolean equalityIsSound(Type leftType, Type rightType) {
        return leftType.getKind() == TypeKind.NULL
                || rightType.getKind() == TypeKind.NULL
                || leftType.isPrimitive()
                || rightType.isPrimitive()
                || !translatesToValue(leftType)
                || !translatesToValue(rightType);
    }

    /** Whether {@code type} may be a Java reference type that translates to a Laurel value. */
    private boolean translatesToValue(Type type) {
        if (type.isPrimitive()) {
            return false;
        }
        // Object is the one type that says nothing about its runtime class, so it must be
        // assumed to hold a value-translated one -- unless it is marked @Impure, which
        // pins it to a heap object.
        if (type.baseType() == symtab.objectType) {
            return !JVerifyUtils.isAnnotated(type, Impure.class);
        }
        // A type VARIABLE says as little about its runtime class as Object does: `T` may
        // be instantiated at String, a boxed primitive or a record, so `==` on it would
        // become structural equality -- the very thing this check rejects. It reaches
        // neither branch above on its own (its baseType is itself, and it is assignable
        // to nothing here), so it needs its own case. Decided by the BOUND, which is the
        // only thing known about the instantiation: an explicit bound that is not itself
        // value-translated pins the runtime class enough to keep `==` a reference
        // comparison. Terminates because Java forbids a cyclic bound.
        // Decided entirely by the BOUND, which is the only thing known about the
        // instantiation -- and which is also where the user can say otherwise. An
        // unbounded `<T>` has upper bound Object, so it inherits Object's answer and is
        // assumed value-translated; `<T extends @Impure Object>` or a bound that is a
        // plain reference type pins the runtime class and permits `==`. Note the
        // annotation has to be read off the BOUND, not the type variable: @Impure is
        // TYPE/TYPE_USE and cannot be written on a type parameter.
        if (type instanceof Type.TypeVar typeVar) {
            Type bound = typeVar.getUpperBound();
            return bound == null || translatesToValue(bound);
        }
        return valueTranslatedTypes().stream()
                .anyMatch(t -> types.isAssignable(type, t) || types.isAssignable(t, type));
    }

    /**
     * The types whose Java reference identity is lost in translation: the boxed
     * primitives (translated as their primitive), {@code String}, and records.
     */
    private List<Type> valueTranslatedTypes() {
        return List.of(
                symtab.booleanType, symtab.byteType, symtab.shortType, symtab.charType,
                symtab.intType, symtab.longType, symtab.floatType, symtab.doubleType,
                symtab.stringType, symtab.recordType);
    }
}
