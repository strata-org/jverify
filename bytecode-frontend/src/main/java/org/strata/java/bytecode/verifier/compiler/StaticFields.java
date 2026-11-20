package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Field;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Static fields and class initialization.
 *
 * <p>A static field is a Laurel global variable. Its initializer is the value
 * the JVM gives the field before any code of the class runs (zero or
 * {@code false}); the value a Java field initializer or a {@code static}
 * block assigns is assigned by the class's {@code <clinit>}, which is
 * translated as an ordinary procedure. Laurel runs a global's initializer at
 * the start of an entry procedure only. Every other procedure starts with an
 * arbitrary value in each global, so a method is verified for every state the
 * static fields can be in when it is called.
 *
 * <p>The JVM runs {@code <clinit>} the first time a class is used: at a
 * {@code new}, at a read or write of one of its static fields, or at a call to
 * one of its static methods. No instruction calls it. A boolean global per
 * class, the <em>initialization flag</em>, records whether it has run, and at
 * each of those instructions the translator emits a guard:
 *
 * <pre>
 *   if (!flag) { flag := true; [guard of the superclass]; call clinit(); flag := true }
 * </pre>
 *
 * The flag is set before {@code <clinit>} runs, as the JVM marks a class before
 * it runs the initializer, so a class that is reached again while it is being
 * initialized is not initialized twice. The flag is arbitrary at the start of
 * a method like any other global, so a method is verified both for a caller
 * that has initialized the class and for one that has not. The assignment
 * after the call states a fact about the JVM (nothing clears the flag): a
 * verifier that knows nothing about what a call did to the globals the callee
 * may write would otherwise lose the flag at the call.
 *
 * <p>The guard of a class contains the guards of the types the JVM initializes
 * before it ({@link ClassInitialization}). No guard is emitted for the class
 * of the method being translated or for the types initialized before it, nor
 * for a type with no initialization code, since initializing it runs nothing.
 *
 * <p>An entry procedure starts with the guard for its own class
 * ({@link #entryPrologue}); {@code <clinit>} starts by assuming its class's
 * static fields at their defaults ({@link #initializerPrologue}).
 *
 * <p>A static field of a class outside the program is a global too, with an
 * arbitrary value and no initialization flag ({@link #declareLibrary}).
 *
 * <p>A read of a static field is a read of the global, saved to Laurel's
 * {@code Stack} at the instruction that reads it, like every other operand
 * (BodyTranslator), so a guard or call emitted later cannot change the value
 * a method already read ({@code int old = C.x++}).
 *
 * <p>A guard is a statement with effects, so it is refused where no statement
 * can be emitted: in a specification, and in a condition that is translated
 * as one expression. A guard whose initializer is not translated (the class
 * is opted out of verification, or its {@code <clinit>} was refused) is
 * refused too: the caller would otherwise take the call for one that
 * changes nothing.
 */
public final class StaticFields {

    /** The name {@code <clinit>} is emitted under: its JVM name, which no Java method can have. */
    public static final String CLINIT_PROCEDURE = "<clinit>";
    private static final String INIT_FLAG = "<initialized>";

    static final String IN_SPECIFICATION = "a use of a class with a static initializer inside a specification"
            + " is not yet supported";
    static final String IN_CONDITIONAL = "a use of a class with a static initializer inside a condition or"
            + " conditional expression is not yet supported";

    /** The globals emitted so far, by name, in first-use order. */
    private final Map<String, Field> globals = new LinkedHashMap<>();

    List<Field> declared() {
        return new ArrayList<>(globals.values());
    }

    /**
     * The global of static field {@code name} of {@code ownerFqn}. A single {@code :}
     * appears in no Java identifier and is not the separator of a static
     * procedure's name, so a field and a method of the same name stay apart.
     */
    static String globalName(String ownerFqn, String name) {
        return ownerFqn + ":" + name;
    }

    /** True iff {@code name} is the name of a global declared here: no local or parameter name contains {@code :}. */
    static boolean isGlobalName(String name) {
        return name.indexOf(':') >= 0 && !name.contains("::");
    }

    static String initFlagName(String fqn) {
        return fqn + ":" + INIT_FLAG;
    }

    /** True iff {@code n} reads a global in place. */
    static boolean readsGlobal(AstNode<StmtExpr> n) {
        if (n.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Local l
                && isGlobalName(l.name().text())) {
            return true;
        }
        for (var child : BodyTranslator.childrenOf(n.val())) {
            if (readsGlobal(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The program class that declares the static field {@code name} that an access
     * through {@code fqn} names (see {@link ClassInitialization#declaringClassOfStatic}),
     * or null if there is none or its type was not registered.
     */
    static String declaringClass(TranslationContext ctx, String fqn, String name) {
        String c = ClassInitialization.declaringClassOfStatic(ctx.resolver, fqn, name);
        if (c == null) {
            return null;
        }
        var fields = ctx.composites.declaredFields.get(c);
        return fields != null && fields.containsKey(name) ? c : null;
    }

    /** Declare the global of a static field on first use and return its name. */
    String declare(TranslationContext ctx, String ownerFqn, String name) {
        String global = globalName(ownerFqn, name);
        if (!globals.containsKey(global)) {
            var info = ctx.composites.declaredFields.get(ownerFqn).get(name);
            var type = info.type().toLaurel(ctx.typeParams, ctx.resolver, ctx.composites);
            globals.put(global, new Field(LaurelFactory.ident(global), true, LaurelFactory.node(type),
                    defaultValue(info.type())));
        }
        return global;
    }

    /**
     * The value the JVM gives a static field before the class's initializer
     * runs. A reference field starts as null, which this translation has no
     * value for, so the initializer of such a global is a hole: an arbitrary
     * value of its type.
     */
    private static Optional<AstNode<StmtExpr>> defaultValue(TypeRef type) {
        if (type.kind() != TypeRef.Kind.PRIMITIVE) {
            return Optional.of(new AstNode<>(new StmtExpr.Hole(false, Optional.empty()), SourceMap.UNKNOWN_RANGE));
        }
        StmtExpr zero = "boolean".equals(type.simpleName())
                ? new StmtExpr.LiteralBool(false) : new StmtExpr.LiteralInt(0);
        return Optional.of(new AstNode<>(zero, SourceMap.UNKNOWN_RANGE));
    }

    /**
     * Declare the global of a static field of a class outside the program
     * (a library class) on first use and return its name. Nothing is known
     * about the field: no initializer of the class is translated, so the
     * global has no initializer and holds an arbitrary value of its type, in
     * an entry procedure too. There is no initialization flag: the class has
     * no translated initializer to run.
     */
    String declareLibrary(TranslationContext ctx, String ownerFqn, String name, String descriptor) {
        String global = globalName(ownerFqn, name);
        if (!globals.containsKey(global)) {
            var type = TypeRef.ofDescriptor(descriptor).toLaurel(ctx.typeParams, ctx.resolver, ctx.composites);
            globals.put(global, new Field(LaurelFactory.ident(global), true, LaurelFactory.node(type),
                    Optional.of(new AstNode<>(new StmtExpr.Hole(false, Optional.empty()), SourceMap.UNKNOWN_RANGE))));
        }
        return global;
    }

    private String declareInitFlag(String fqn) {
        String flag = initFlagName(fqn);
        globals.computeIfAbsent(flag, f -> new Field(LaurelFactory.ident(f), true,
                LaurelFactory.node(new HighType.TBool()),
                Optional.of(new AstNode<>(new StmtExpr.LiteralBool(false), SourceMap.UNKNOWN_RANGE))));
        return flag;
    }

    /** See {@link ClassInitialization#initializerOf}. */
    static java.lang.classfile.MethodModel initializerOf(TranslationContext ctx, String fqn) {
        return ClassInitialization.initializerOf(ctx.resolver, fqn);
    }

    static boolean isAssertionsDisabled(TranslationContext ctx, java.lang.classfile.instruction.FieldInstruction fi) {
        return ClassInitialization.isAssertionsDisabled(ctx.resolver, fi);
    }

    static boolean isEnumValues(TranslationContext ctx, java.lang.classfile.instruction.FieldInstruction fi) {
        return ClassInitialization.isEnumValues(ctx.resolver, fi);
    }

    static boolean isEnumValuesMethod(TranslationContext ctx, java.lang.classfile.instruction.InvokeInstruction inv) {
        return ClassInitialization.isEnumValuesMethod(ctx.resolver, inv);
    }

    private static List<String> initializedBefore(TranslationContext ctx, String fqn) {
        return ClassInitialization.initializedBefore(ctx.resolver, fqn);
    }

    private static java.util.Set<String> initializationStarted(TranslationContext ctx) {
        return ClassInitialization.initializationStarted(ctx.resolver, ctx.currentClassFqn);
    }

    private static boolean runsInitializer(TranslationContext ctx, String fqn, java.util.Set<String> started) {
        return ClassInitialization.runsInitializer(ctx.resolver, fqn, started);
    }

    /**
     * Emit the guard for a use of class {@code fqn}, if it needs one.
     * {@code operands} are the instruction's operands, already popped from the
     * stack; they are returned unchanged, saved to the {@code Stack} at their
     * own instructions, so the guard cannot change them.
     */
    List<AstNode<StmtExpr>> initialize(BodyTranslator b, String fqn, List<AstNode<StmtExpr>> operands) {
        TranslationContext ctx = b.ctx;
        var started = initializationStarted(ctx);
        if (!runsInitializer(ctx, fqn, started)) {
            return operands;
        }
        if (ctx.method.inContract || ctx.method.pure || ctx.contractMode) {
            throw new JavaViolationException(IN_SPECIFICATION);
        }
        if (b.inConditional()) {
            throw new JavaViolationException(IN_CONDITIONAL);
        }
        b.addStmt(guard(b, fqn, started));
        return operands;
    }

    /**
     * The statements that start the body of an entry procedure: the guard for
     * its own class. The JVM initializes a class before it runs the class's
     * {@code main}, and an entry procedure starts with every flag false, so no
     * use of the class may be left unguarded here. Empty when initializing the
     * class runs nothing.
     */
    List<StmtExpr> entryPrologue(BodyTranslator b) {
        TranslationContext ctx = b.ctx;
        if (ctx.currentClassFqn == null || !runsInitializer(ctx, ctx.currentClassFqn, java.util.Set.of())) {
            return List.of();
        }
        return List.of(guard(b, ctx.currentClassFqn, java.util.Set.of()));
    }

    /**
     * The statements that start the body of {@code <clinit>} of {@code fqn}:
     * one assignment per static field of a primitive type of its JVM default
     * value, which is the field's value at that point when
     * ({@link ClassInitialization#staticsAtDefaultsInInitializer}). A
     * reference field is left out: this translation has no value for null.
     */
    List<StmtExpr> initializerPrologue(BodyTranslator b, String fqn) {
        TranslationContext ctx = b.ctx;
        var out = new ArrayList<StmtExpr>();
        var cm = ctx.resolver.programClassModels.get(fqn);
        var fields = ctx.composites.declaredFields.get(fqn);
        if (cm == null || fields == null || !ClassInitialization.staticsAtDefaultsInInitializer(ctx.resolver, fqn)) {
            return out;
        }
        for (var fm : cm.fields()) {
            int flags = fm.flags().flagsMask();
            String name = fm.fieldName().stringValue();
            if ((flags & java.lang.classfile.ClassFile.ACC_STATIC) == 0
                    || (flags & java.lang.classfile.ClassFile.ACC_SYNTHETIC) != 0 || !fields.containsKey(name)) {
                continue;
            }
            var value = defaultValue(fields.get(name).type());
            if (value.isEmpty() || value.get().val() instanceof StmtExpr.Hole) {
                continue;
            }
            String global = declare(ctx, fqn, name);
            // An assignment, not an assumption: the same fact, and it runs on the interpreter.
            out.add(assign(global, value.get()));
        }
        return out;
    }

    /**
     * {@code if (!flag) { flag := true; <guards of the types initialized before>; <clinit>(); flag := true }}
     * for {@code fqn}, which {@link #runsInitializer} holds for. A type on the
     * way with nothing of its own to run contributes only the guards of the
     * types before it.
     */
    private StmtExpr guard(BodyTranslator b, String fqn, java.util.Set<String> started) {
        TranslationContext ctx = b.ctx;
        var body = new ArrayList<AstNode<StmtExpr>>();
        var inner = new ArrayList<AstNode<StmtExpr>>();
        for (String before : initializedBefore(ctx, fqn)) {
            if (runsInitializer(ctx, before, started)) {
                inner.add(b.e(guard(b, before, started)));
            }
        }
        var clinit = initializerOf(ctx, fqn);
        if (clinit == null) {
            // Nothing of its own to run and no flag to set: only the types before it.
            return new StmtExpr.Block(inner, Optional.empty(), List.of(), Optional.empty());
        }
        if (ctx.resolver.isOptedOut(ctx.resolver.programClassModels.get(fqn), clinit)) {
            throw new JavaViolationException(notVerified(fqn));
        }
        String flag = declareInitFlag(fqn);
        body.add(b.e(assign(flag, b.e(new StmtExpr.LiteralBool(true)))));
        body.addAll(inner);
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(fqn + "#<clinit>");
        }
        body.add(b.e(LaurelFactory.staticCall(
                LaurelFactory.ident(JavaNames.staticProcedure(fqn, CLINIT_PROCEDURE)), List.of(), List.of())));
        body.add(b.e(assign(flag, b.e(new StmtExpr.LiteralBool(true)))));
        return new StmtExpr.IfThenElse(
                b.e(LaurelFactory.primitiveOp(new Operation.Not(), b.e(LaurelFactory.var_(flag)))),
                b.e(new StmtExpr.Block(body, Optional.empty(), List.of(), Optional.empty())),
                Optional.empty());
    }

    static StmtExpr assign(String variable, AstNode<StmtExpr> value) {
        return new StmtExpr.Assign(List.of(new AstNode<Variable>(
                new Variable.Local(LaurelFactory.ident(variable)), SourceMap.UNKNOWN_RANGE)), value);
    }

    /** The refusal of a method whose use of {@code fqn} would run an initializer that is not verified. */
    static String notVerified(String fqn) {
        return "the static initializer of '" + JavaNames.simpleName(fqn) + "', which this method may run, is not verified";
    }
}
