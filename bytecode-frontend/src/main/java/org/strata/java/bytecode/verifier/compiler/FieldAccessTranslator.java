package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import org.strata.java.bytecode.frontend.ClassfileQueries;
import org.strata.java.bytecode.frontend.Locals;
import org.strata.java.bytecode.frontend.MethodBodyFactory;
import org.strata.java.bytecode.frontend.Span;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates field access instructions: get/put on instance and static
 * fields, super-field-read refusal ordering, and composite-sort field
 * registration resolved from the receiver's static type.
 */
final class FieldAccessTranslator {

    private final BodyTranslator b;
    private final TranslationContext ctx;

    FieldAccessTranslator(BodyTranslator b) {
        this.b = b;
        this.ctx = b.ctx;
    }

    void field(java.lang.classfile.instruction.FieldInstruction fi) {
        TypeRef ownerRef = TypeRef.ofClassInternal(fi.owner().asInternalName(), false, false);
        String fn = fi.name().stringValue();
        String fdesc = fi.typeSymbol().descriptorString();
        if (fdesc.startsWith("[") && (fi.opcode() == java.lang.classfile.Opcode.GETFIELD
                || fi.opcode() == java.lang.classfile.Opcode.PUTFIELD) && ctx.composites.arrays.enabled()) {
            ctx.composites.arrays.refuseInCodeWithoutDebugInfo();
        }
        switch (fi.opcode()) {
            case GETSTATIC -> {
                String declaring = staticOwner(ownerRef, fn);
                if (declaring != null) {
                    readStatic(declaring, fn, fdesc);
                } else if (ctx.resolver.knownClasses.containsKey(ownerRef.nameWithScope())) {
                    // A program class that does not declare the field: a read
                    // through a class of a field declared outside the program.
                    AstNode<StmtExpr> n = b.push(new StmtExpr.LiteralString("$getstatic"));
                    b.staticTypeOf.put(n, TypeRef.ofDescriptor(fdesc));
                    b.defer(n, "Unsupported field access: "
                            + ownerRef.nameWithScope() + "." + fn);
                    b.noteDesc(n, fdesc);
                } else {
                    readLibraryStatic(JavaNames.dottedFqn(fi.owner().asInternalName()), fn, fdesc);
                }
            }
            case GETFIELD -> {
                AstNode<StmtExpr> recv = b.stack.isEmpty()
                        ? b.e(new StmtExpr.LiteralString("?")) : b.stack.pop();
                boolean superRead = isSuperFieldRead(fi, recv);
                TypeRef declared = declareAccessedField(recv, fn);
                if (superRead) {
                    // A super-field read refuses AFTER the field-registration
                    // side effects (an ordering pinned by the corpus).
                    AstNode<StmtExpr> n = b.push(new StmtExpr.LiteralString("$superfield"));
                    b.defer(n, "Unsupported expression: SuperExpr");
                    b.noteDesc(n, fdesc);
                    return;
                }
                AstNode<StmtExpr> n = b.push(new StmtExpr.Var(
                        new Variable.Field(recv, LaurelFactory.ident(fn))));
                b.noteDesc(n, fdesc);
                if (fdesc.startsWith("L")) {
                    // Every reference field is read as non-null, but may hold null.
                    b.heapReadValues.add(n);
                }
                if (fdesc.startsWith("L")
                        || (fdesc.startsWith("[") && ctx.composites.arrays.enabled())) {
                    b.staticTypeOf.put(n, TypeRef.ofDescriptor(fdesc));
                } else {
                    // A primitive field read is typed by its declaration in
                    // every context, so an @Unbounded field reads as the
                    // unbounded Laurel int: javac's widening of it is the
                    // identity (see BodyTranslator.convert) and its arithmetic
                    // carries no overflow obligation
                    // (BodyTranslator.checkedBin).
                    b.staticTypeOf.put(n, declared);
                }
            }
            case PUTSTATIC -> {
                AstNode<StmtExpr> v = b.coerce(b.stack.pop(), fdesc);
                if (StaticFields.isAssertionsDisabled(ctx, fi)) {
                    // Assertions are translated as always enabled and the field
                    // is never read, so the assignment in <clinit> is dropped.
                    return;
                }
                if (StaticFields.isEnumValues(ctx, fi)) {
                    // The array behind values() is not modelled; values() itself
                    // is not translated, so nothing reads it.
                    return;
                }
                b.arrays.refuseAsObject(v, fdesc);
                if (ctx.method.pure) {
                    // A transparent body is a function of its inputs; callers see
                    // through it and expect no assignment in it.
                    throw new JavaViolationException(
                            "a static field assignment in a @Transparent method is not yet supported");
                }
                String declaring = staticOwner(ownerRef, fn);
                if (declaring == null) {
                    throw new JavaViolationException("Unsupported field access: "
                            + ownerRef.nameWithScope() + "." + fn);
                }
                v = ctx.statics.initialize(b, declaring, List.of(v)).get(0);
                b.addStmt(StaticFields.assign(ctx.statics.declare(ctx, declaring, fn), v));
            }
            case PUTFIELD -> {
                AstNode<StmtExpr> v = b.coerce(b.stack.pop(), fdesc);
                b.arrays.refuseAsObject(v, fdesc);
                AstNode<StmtExpr> recv = b.stack.isEmpty()
                        ? b.e(new StmtExpr.LiteralString("?")) : b.stack.pop();
                if (fn.equals("this$0")) {
                    return;    // compiler wiring; no source form
                }
                boolean superWrite = isSuperFieldRead(fi, recv);
                var target = new AstNode<Variable>(
                        new Variable.Field(recv, LaurelFactory.ident(fn)), SourceMap.UNKNOWN_RANGE);
                declareAccessedField(recv, fn);
                if (superWrite) {
                    throw new JavaViolationException("Unsupported expression: SuperExpr");
                }
                b.addStmt(new StmtExpr.Assign(List.of(target), v));
            }
            default -> { }
        }
    }

    /** A super-field read: `this` receiver, foreign owner, shadowed here. */
    boolean isSuperFieldRead(java.lang.classfile.instruction.FieldInstruction fi,
                                     AstNode<StmtExpr> recv) {
        if (b.cm == null) {
            return false;
        }
        boolean isThis = recv.val() instanceof StmtExpr.Var var
                && var.var_() instanceof Variable.Local l
                && l.name().text().equals(LaurelFactory.LAUREL_SELF_BINDING);
        if (!isThis || fi.owner().asInternalName().equals(b.cm.thisClass().asInternalName())) {
            return false;
        }
        String fn = fi.name().stringValue();
        for (var f : b.cm.fields()) {
            if (f.fieldName().stringValue().equals(fn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve the owning composite from the receiver's static type
     * (fallback: the unique declaring class), then declare the accessed
     * field on that composite (registering it as referenced). Contract
     * code has no fallback: several contract classes declare fields of the
     * same name, so only the receiver's own sort may own the field.
     *
     * @return the field's declared type (with its {@code @Nat}/{@code @Unbounded} flags)
     */
    TypeRef declareAccessedField(AstNode<StmtExpr> recv, String fieldName) {
        String ownerFqn = b.staticTypeFqnOf(recv);
        if (ownerFqn == null || !hasDeclaredField(ownerFqn, fieldName)) {
            ownerFqn = null;
            if (!ctx.contractMode) {
                for (var e : ctx.composites.declaredFields.entrySet()) {
                    if (e.getValue().containsKey(fieldName)) {
                        ownerFqn = e.getKey();
                        break;
                    }
                }
            }
        }
        if (ownerFqn == null) {
            throw new JavaViolationException("Unsupported field access: "
                    + renderReceiver(recv) + "." + fieldName);
        }
        // A relocated contract's field lives on its contractee (ClassResolver.contracteeOf).
        ownerFqn = ctx.resolver.contracteeOf.getOrDefault(ownerFqn, ownerFqn);
        var info = ctx.composites.declaredFields.get(ownerFqn).get(fieldName);
        if (info.nullable() && info.type().kind() == TypeRef.Kind.ARRAY && ctx.composites.arrays.enabled()) {
            // Null is not modelled: refuse every method that accesses the field.
            throw new JavaViolationException(ArrayModel.NULLABLE);
        }
        ctx.composites.referencedCompositeTypes.add(ownerFqn);
        var fields = ctx.composites.compositeFields
                .computeIfAbsent(ownerFqn, k -> new java.util.LinkedHashMap<>());
        if (!fields.containsKey(fieldName)) {
            fields.put(fieldName, new org.strata.jverify.laurel.Field(
                    LaurelFactory.ident(fieldName), !info.isFinal(),
                    LaurelFactory.node(info.type().toLaurel(ctx.typeParams, ctx.resolver, ctx.composites)),
                    Optional.empty()));
        }
        return info.type();
    }

    /** The program class that declares the static field an access names, or null. */
    private String staticOwner(TypeRef ownerRef, String fieldName) {
        String fqn = ctx.resolver.knownClasses.get(ownerRef.nameWithScope());
        return fqn == null ? null : StaticFields.declaringClass(ctx, fqn, fieldName);
    }

    /** A read of a static field: a read of its global (see {@link StaticFields} for how the value is kept). */
    private void readStatic(String declaring, String fieldName, String fdesc) {
        ctx.statics.initialize(b, declaring, List.of());
        String global = ctx.statics.declare(ctx, declaring, fieldName);
        TypeRef declared = ctx.composites.declaredFields.get(declaring).get(fieldName).type();
        AstNode<StmtExpr> n = b.push(LaurelFactory.var_(global));
        b.noteDesc(n, fdesc);
        if (fdesc.startsWith("L")) {
            // As for an instance field: a reference field may hold null.
            b.heapReadValues.add(n);
        }
        if (fdesc.startsWith("L") || (fdesc.startsWith("[") && ctx.composites.arrays.enabled())) {
            b.staticTypeOf.put(n, TypeRef.ofDescriptor(fdesc));
        } else {
            b.staticTypeOf.put(n, declared);
        }
    }

    /** A read of a static field of a class outside the program: a read of its global, with nothing known about the value. */
    private void readLibraryStatic(String ownerFqn, String fieldName, String fdesc) {
        String global = ctx.statics.declareLibrary(ctx, ownerFqn, fieldName, fdesc);
        AstNode<StmtExpr> n = b.push(LaurelFactory.var_(global));
        b.noteDesc(n, fdesc);
        if (fdesc.startsWith("L")) {
            b.heapReadValues.add(n);
        }
        b.staticTypeOf.put(n, TypeRef.ofDescriptor(fdesc));
    }

    private boolean hasDeclaredField(String fqn, String fieldName) {
        var fields = ctx.composites.declaredFields.get(fqn);
        return fields != null && fields.containsKey(fieldName);
    }

    /** Best-effort receiver rendering for the "Unsupported field access" message. */
    private String renderReceiver(AstNode<StmtExpr> recv) {
        if (recv.val() instanceof StmtExpr.Var var && var.var_() instanceof Variable.Local l) {
            return l.name().text().equals(LaurelFactory.LAUREL_SELF_BINDING) ? "this" : l.name().text();
        }
        return "?";
    }
}
