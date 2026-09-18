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
        switch (fi.opcode()) {
            case GETSTATIC -> {
                // Old shape: FieldAccessExpr(NameExpr(owner), fn). Conversion:
                // program-class owner -> "static field access is not yet
                // supported"; anything else falls into ensureFieldOnComposite's
                // unique-field fallback or its refusal.
                if (ctx.resolver.knownClasses.containsKey(ownerRef.nameWithScope())) {
                    AstNode<StmtExpr> n = b.push(new StmtExpr.LiteralString("$getstatic"));
                    b.staticTypeOf.put(n, TypeRef.ofDescriptor(fdesc));
                    b.defer(n, "static field access is not yet supported");
                    b.noteDesc(n, fdesc);
                } else {
                    AstNode<StmtExpr> n = b.push(new StmtExpr.LiteralString("$getstatic"));
                    b.staticTypeOf.put(n, TypeRef.ofDescriptor(fdesc));
                    b.defer(n, "Unsupported field access: "
                            + ownerRef.nameWithScope() + "." + fn);
                    b.noteDesc(n, fdesc);
                }
            }
            case GETFIELD -> {
                AstNode<StmtExpr> recv = b.stack.isEmpty()
                        ? b.e(new StmtExpr.LiteralString("?")) : b.stack.pop();
                if (fn.equals("this$0")) {
                    // Outer.this: the conversion lowered any ThisExpr to self?.
                    AstNode<StmtExpr> n = b.push(LaurelFactory.var_(LaurelFactory.LAUREL_SELF_BINDING));
                    b.noteDesc(n, fdesc);
                    return;
                }
                boolean superRead = isSuperFieldRead(fi, recv);
                declareAccessedField(recv, fn);
                if (superRead) {
                    // A super-field read refuses AFTER the field-registration
                    // side effects (JFE's ordering).
                    AstNode<StmtExpr> n = b.push(new StmtExpr.LiteralString("$superfield"));
                    b.defer(n, "Unsupported expression: SuperExpr");
                    b.noteDesc(n, fdesc);
                    return;
                }
                AstNode<StmtExpr> n = b.push(new StmtExpr.Var(
                        new Variable.Field(recv, LaurelFactory.ident(fn))));
                b.noteDesc(n, fdesc);
                if (fdesc.startsWith("L")) {
                    b.staticTypeOf.put(n, TypeRef.ofDescriptor(fdesc));
                }
            }
            case PUTSTATIC -> {
                AstNode<StmtExpr> v = b.coerce(b.stack.pop(), fdesc);
                // Old: AssignExpr(FieldAccessExpr(owner, fn)) -> convertVariable
                // -> refuseStaticFieldAccess.
                if (ctx.resolver.knownClasses.containsKey(ownerRef.nameWithScope())) {
                    throw new JavaViolationException("static field access is not yet supported");
                }
                throw new JavaViolationException("Unsupported field access: "
                        + ownerRef.nameWithScope() + "." + fn);
            }
            case PUTFIELD -> {
                AstNode<StmtExpr> v = b.coerce(b.stack.pop(), fdesc);
                AstNode<StmtExpr> recv = b.stack.isEmpty()
                        ? b.e(new StmtExpr.LiteralString("?")) : b.stack.pop();
                if (fn.equals("this$0")) {
                    return;    // compiler wiring; no source form
                }
                boolean superWrite = isSuperFieldRead(fi, recv);
                var target = new AstNode<Variable>(
                        new Variable.Field(recv, LaurelFactory.ident(fn)), SourceMap.UNKNOWN_RANGE);
                if (b.asIncrementExprOn(target, v)) {
                    return;
                }
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
     * field on it (JFE's ensureFieldOnComposite).
     */
    void declareAccessedField(AstNode<StmtExpr> recv, String fieldName) {
        String ownerFqn = b.staticTypeFqnOf(recv);
        if (ownerFqn == null || !hasDeclaredField(ownerFqn, fieldName)) {
            ownerFqn = null;
            for (var e : ctx.composites.declaredFields.entrySet()) {
                if (e.getValue().containsKey(fieldName)) {
                    ownerFqn = e.getKey();
                    break;
                }
            }
        }
        if (ownerFqn == null) {
            throw new JavaViolationException("Unsupported field access: "
                    + renderReceiver(recv) + "." + fieldName);
        }
        var info = ctx.composites.declaredFields.get(ownerFqn).get(fieldName);
        ctx.composites.referencedCompositeTypes.add(ownerFqn);
        var fields = ctx.composites.compositeFields
                .computeIfAbsent(ownerFqn, k -> new java.util.LinkedHashMap<>());
        if (!fields.containsKey(fieldName)) {
            fields.put(fieldName, new org.strata.jverify.laurel.Field(
                    LaurelFactory.ident(fieldName), !info.isFinal(),
                    LaurelFactory.node(info.type().toLaurel(ctx.typeParams, ctx.resolver, ctx.composites)),
                    Optional.empty()));
        }
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
