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
 * Translates invocation instructions into Laurel calls: instance/static
 * invokes (with receiver typing, super-call refusals, inner-ctor
 * enclosing-instance handling), invokedynamic (string concat recipes and
 * lambda/method-reference handles), and call finishing (return-descriptor
 * bookkeeping, callee edges). JVerify spec calls are recognized here and
 * handed to {@link SpecCallTranslator}.
 */
final class InvokeTranslator {

    /** The exception a contract method throws to say it has no implementation. */
    private static final String CONTRACT_EXCEPTION_INTERNAL = "org/strata/jverify/ContractException";

    private final BodyTranslator b;
    private final TranslationContext ctx;

    InvokeTranslator(BodyTranslator b) {
        this.b = b;
        this.ctx = b.ctx;
    }

    void invoke(java.lang.classfile.instruction.InvokeInstruction inv) {
        var d = inv.typeSymbol();
        int n = d.parameterCount();
        boolean isStatic = inv.opcode() == java.lang.classfile.Opcode.INVOKESTATIC;
        boolean isInit = inv.opcode() == java.lang.classfile.Opcode.INVOKESPECIAL
                && inv.name().stringValue().equals("<init>");
        String ownerInternal = inv.owner().asInternalName();
        String name = inv.name().stringValue();

        if (isStatic && isJVerifyOwner(ownerInternal)) {
            b.specCalls.jverifyInvoke(inv, name, n, d);
            return;
        }

        List<AstNode<StmtExpr>> args = b.popArgs(n);
        for (int i = 0; i < n; i++) {
            args.set(i, b.coerceArg(args.get(i), d.parameterType(i).descriptorString()));
        }

        if (isInit) {
            AstNode<StmtExpr> recv = b.stack.isEmpty() ? b.e(new StmtExpr.LiteralString("?")) : b.stack.pop();
            String g = b.pendingNew.get(recv);
            if (g == null && b.inCtor) {
                // Explicit constructor invocation: constructors are never
                // translated, so this only guards misuse.
                throw new JavaViolationException(
                        "Unsupported statement: ExplicitConstructorInvocationStmt");
            }
            if (g == null) {
                g = ownerInternal;
            }
            if (!b.stack.isEmpty() && b.stack.peek() == recv) {
                b.stack.pop();
            }
            if (ctx.contractMode && g.equals(CONTRACT_EXCEPTION_INTERNAL)) {
                // `throw new ContractException()` in contract code: a marker
                // the ATHROW turns into the no-implementation statement.
                b.push(new StmtExpr.LiteralString(BodyTranslator.CONTRACT_EXCEPTION_MARKER));
                return;
            }
            // Inner-class enclosing-instance argument becomes the scope in
            // source; the conversion ignored the scope, so only the
            // null-check statement removal matters here.
            AstNode<StmtExpr> outer = innerCtorOuter(g, d, args);
            List<AstNode<StmtExpr>> ctorArgs = args;
            if (outer != null) {
                dropNullCheckOf(outer);
                ctorArgs = args.subList(1, args.size());
            }
            // Allocation: refused in a contract (checked at
            // spec-consumption), the sort registered, ctor args
            // translated-and-discarded.
            TypeRef t = TypeRef.ofClassInternal(g, false, false);
            String sortName = ctx.resolver.qualifyTypeName(t.nameWithScope());
            ctx.composites.referencedCompositeTypes.add(sortName);
            AstNode<StmtExpr> nw = b.push(new StmtExpr.New(LaurelFactory.ident(sortName), List.of()));
            b.callNodes.add(nw);
            b.staticTypeOf.put(nw, t);
            return;
        }

        boolean superCall = inv.opcode() == java.lang.classfile.Opcode.INVOKESPECIAL && b.cm != null
                && !ownerInternal.equals(b.cm.thisClass().asInternalName());
        if (isStatic) {
            staticInvoke(inv, ownerInternal, name, args, d);
            return;
        }

        AstNode<StmtExpr> recv = b.stack.isEmpty() ? b.e(new StmtExpr.LiteralString("?")) : b.stack.pop();
        if (superCall) {
            // Receiver-shape refusal before argument handling.
            throw new JavaViolationException("super call is not yet supported");
        }
        instanceInvoke(recv, ownerInternal, name, args, d);
    }

    private boolean isJVerifyOwner(String ownerInternal) {
        return ownerInternal.equals("org/strata/jverify/JVerify")
                || ownerInternal.equals("org/strata/jverify/Verify");
    }

    private boolean isThrowsOnCaseOwner(String ownerInternal) {
        return ownerInternal.endsWith("/ThrowsOnCase") || ownerInternal.endsWith("$ThrowsOnCase");
    }

    /** Static user call (fused convertCall's static arm). */
    private void staticInvoke(java.lang.classfile.instruction.InvokeInstruction inv,
                              String ownerInternal, String bareName,
                              List<AstNode<StmtExpr>> args,
                              java.lang.constant.MethodTypeDesc d) {
        if (!ctx.resolver.programClassModels.containsKey(JavaNames.dottedFqn(ownerInternal))) {
            ContractMember cm = ctx.contractMode
                    ? ctx.contracts.lookupContractCode(ownerInternal, bareName, d, true)
                    : ctx.contracts.lookupStatic(ownerInternal, bareName, d);
            if (cm != null && !typeVarArgumentTrips(cm, args, d)) {
                refuseStringValuedArguments(bareName, args, d);
                String procName = cm.sortOrFqn() + "_" + cm.laurelName();
                AstNode<StmtExpr> call = b.e(new StmtExpr.StaticCall(LaurelFactory.ident(procName), args));
                recordContractCall(cm, call, procName);
                finishCall(call, d, cm.returnsTypeVar());
                return;
            }
        }
        TypeRef ownerRef = TypeRef.ofClassInternal(ownerInternal, false, false);
        String fqn = ctx.resolver.knownClasses.get(ownerRef.nameWithScope());
        if (fqn == null) {
            throw new JavaViolationException(
                    "Call to unknown class '" + ownerRef.nameWithScope() + "' is not supported");
        }
        ctx.resolver.refuseIfOverloaded(fqn, bareName, true);
        var staticCm = ctx.resolver.programClassModels.get(fqn);
        boolean resultIsTypeVar = false;
        if (staticCm != null) {
            for (var m : staticCm.methods()) {
                if (m.methodName().stringValue().equals(bareName)
                        && (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE)) == 0
                        && (m.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_STATIC) != 0
                        && m.methodTypeSymbol().parameterCount() == args.size()) {
                    if (ctx.resolver.isOptedOut(staticCm, m)) {
                        throw new JavaViolationException(
                                "call to '" + bareName + "', which could not be translated");
                    }
                    // Unique: refuseIfOverloaded ran above.
                    resultIsTypeVar = returnsTypeVar(m);
                }
            }
        }
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(fqn + "#" + bareName);
        }
        AstNode<StmtExpr> call = b.e(new StmtExpr.StaticCall(
                LaurelFactory.ident(fqn + "_" + bareName), args));
        b.callNodes.add(call);
        b.calleeKeys.put(call.val(), fqn + "_" + bareName);
        finishCall(call, d, resultIsTypeVar);
    }

    /** Instance user call (fused convertCall's value-receiver arm). */
    private void instanceInvoke(AstNode<StmtExpr> recv, String ownerInternal, String bareName,
                                List<AstNode<StmtExpr>> args,
                                java.lang.constant.MethodTypeDesc d) {
        // A detached throwsOn handle: a chained clause must ride the chain.
        if (b.specStmts.containsKey(recv)) {
            BodyTranslator.SpecStmt spec = b.specStmts.get(recv);
            if (spec.name().equals("throwsOn")) {
                if (!bareName.equals("postcondition") && !bareName.equals("modifies")) {
                    throw new JavaViolationException(
                            "Unsupported throwsOn case clause: " + bareName);
                }
                if (args.size() != 1) {
                    throw new JavaViolationException("A throwsOn case's " + bareName
                            + " clause must have exactly one argument");
                }
                spec.links().add(new BodyTranslator.SpecLink(bareName, args.get(0), b.lambdas.get(args.get(0))));
                b.stack.push(recv);    // the chain returns the handle
                return;
            }
        }
        TypeRef recvType = b.staticTypeOf.get(recv);
        if (recvType != null && "ThrowsOnCase".equals(recvType.simpleName())) {
            throw new JavaViolationException(
                    "a throwsOn case clause must chain directly off throwsOn(...); calling '"
                            + bareName + "' on a captured handle detaches it from its case");
        }
        // String literal receiver: a literal is a Laurel string, not the
        // String composite, so the call always refuses. Warn when no contract
        // member matches, then check the args (an array-initializer argument
        // reports its own refusal first — the source frontend converts
        // arguments before the receiver refuses), then refuse.
        if (recv.val() instanceof StmtExpr.LiteralString s && b.descOf.get(recv) != null
                && b.descOf.get(recv).equals("Ljava/lang/String;")) {
            ctx.src.defaultRange = b.stmtRange(b.pos, false);
            if (!ctx.contractMode && ctx.contracts.lookupInstance(ownerInternal, bareName, d) == null) {
                ctx.reporter.reportWarning(ctx.src.uri, ctx.src.positionRange(ctx.src.defaultRange),
                        "missing contract for method '" + bareName + "' in class 'java.lang.String'");
            }
            for (var a : args) {
                b.checkPlaceable(a);
            }
            throw new JavaViolationException("Unsupported call: " + bareName);
        }
        // Freshly-allocated receiver.
        if (recv.val() instanceof StmtExpr.New) {
            throw new JavaViolationException(
                    "method call on a freshly-allocated receiver is not yet supported");
        }
        // Contract code: Strata rejects an instance call whose receiver is
        // another call's result (`chars.drop(n).take(m)`), so the member
        // that contains one is refused.
        if (ctx.contractMode && (recv.val() instanceof StmtExpr.InstanceCall
                || recv.val() instanceof StmtExpr.StaticCall)) {
            throw new JavaViolationException("call to '" + bareName
                    + "' on the result of another call is not yet supported");
        }
        String receiverFqn = b.staticTypeFqnOf(recv);
        ContractMember member = contractMemberFor(recv, receiverFqn, ownerInternal, bareName, args, d);
        if (member != null) {
            refuseStringValuedArguments(bareName, args, d);
            AstNode<StmtExpr> call = b.e(new StmtExpr.InstanceCall(
                    recv, LaurelFactory.ident(member.laurelName()), args));
            recordContractCall(member, call, member.sortOrFqn() + "_" + member.laurelName());
            finishCall(call, d, member.returnsTypeVar());
            return;
        }
        if (receiverFqn == null || !ctx.resolver.programClassModels.containsKey(receiverFqn)) {
            throw new JavaViolationException("call to '" + bareName
                    + "' on a receiver whose static type is not a class is not yet supported");
        }
        var callee = ctx.resolver.findCallee(receiverFqn, bareName);
        if (callee == null) {
            throw new JavaViolationException("call to '" + bareName
                    + "' on a receiver whose static type is not a class is not yet supported");
        }
        if (ctx.resolver.isOptedOut(ctx.resolver.programClassModels.get(callee.declaringFqn()),
                callee.method())) {
            throw new JavaViolationException(
                    "call to '" + bareName + "', which could not be translated");
        }
        ctx.resolver.enforceDispatchGate(receiverFqn, callee, bareName);
        ctx.resolver.enforceAncestryOverloadGate(receiverFqn, bareName);
        if (!callee.declaringFqn().equals(receiverFqn)) {
            for (String fqn = receiverFqn;
                    fqn != null && !fqn.equals(callee.declaringFqn());
                    fqn = ctx.resolver.programSuper.get(fqn)) {
                String parent = ctx.resolver.programSuper.get(fqn);
                if (parent == null) {
                    break;
                }
                ctx.composites.linkExtends(fqn, parent);
            }
        }
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(callee.declaringFqn() + "#" + bareName);
        }
        AstNode<StmtExpr> call = b.e(new StmtExpr.InstanceCall(recv, LaurelFactory.ident(bareName), args));
        b.callNodes.add(call);
        b.calleeKeys.put(call.val(), callee.declaringFqn() + "_" + bareName);
        finishCall(call, callee.method().methodTypeSymbol(), returnsTypeVar(callee.method()));
    }

    /**
     * The library contract member an instance call binds to, or null. Only a
     * typed composite receiver qualifies: not a literal or {@code $}-marker,
     * not another call's result (Strata rejects that receiver), not a value
     * whose Laurel type is a type variable, and not a program class. The member's sort must be the receiver's own sort or
     * {@code Object}, because library sorts extend only {@code Object} in the
     * emitted Laurel. A type-variable argument for a non-type-variable
     * reference parameter makes the call a miss (see
     * {@link LibraryContracts#REFUSE_TYPE_VAR_REFERENCE_ARGS}).
     */
    private ContractMember contractMemberFor(AstNode<StmtExpr> recv, String receiverFqn,
                                             String ownerInternal, String bareName,
                                             List<AstNode<StmtExpr>> args,
                                             java.lang.constant.MethodTypeDesc d) {
        if (receiverFqn == null || recv.val() instanceof StmtExpr.LiteralString
                || recv.val() instanceof StmtExpr.InstanceCall || recv.val() instanceof StmtExpr.StaticCall
                || b.typeVarValues.contains(recv)
                || ctx.resolver.programClassModels.containsKey(receiverFqn)) {
            return null;
        }
        boolean selfRecv = recv.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Local l
                && l.name().text().equals(LaurelFactory.LAUREL_SELF_BINDING);
        TypeRef rt = b.staticTypeOf.get(recv);
        boolean typedComposite = selfRecv || (rt != null && rt.kind() == TypeRef.Kind.CLASS
                && (rt.scoped() || !ctx.typeParams.contains(rt.simpleName())));
        if (!typedComposite) {
            return null;
        }
        ContractMember cm = ctx.contractMode
                ? ctx.contracts.lookupContractCode(ownerInternal, bareName, d, false)
                : ctx.contracts.lookupInstance(ownerInternal, bareName, d);
        if (cm == null || !(cm.sortOrFqn().equals(receiverFqn)
                || cm.sortOrFqn().equals(CompositeModel.OBJECT_SORT))) {
            return null;
        }
        return typeVarArgumentTrips(cm, args, d) ? null : cm;
    }

    /**
     * True when a value whose Laurel type is a type variable meets a
     * reference parameter that is not itself a type variable: Strata has no
     * bound slot on type parameters, so the call is treated as a lookup miss.
     */
    private boolean typeVarArgumentTrips(ContractMember cm, List<AstNode<StmtExpr>> args,
                                         java.lang.constant.MethodTypeDesc d) {
        if (!LibraryContracts.REFUSE_TYPE_VAR_REFERENCE_ARGS) {
            return false;
        }
        for (int i = 0; i < args.size(); i++) {
            if (b.typeVarValues.contains(args.get(i))
                    && d.parameterType(i).descriptorString().startsWith("L")
                    && !(i < cm.paramIsTypeVar().size() && cm.paramIsTypeVar().get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Refuse a library call that passes a string literal or concatenation
     * (a Laurel string, not the String composite) for a reference parameter
     * (see {@link LibraryContracts#REFUSE_LITERAL_REFERENCE_ARGS}).
     */
    private void refuseStringValuedArguments(String bareName, List<AstNode<StmtExpr>> args,
                                             java.lang.constant.MethodTypeDesc d) {
        if (!LibraryContracts.REFUSE_LITERAL_REFERENCE_ARGS) {
            return;
        }
        for (int i = 0; i < args.size(); i++) {
            var a = args.get(i);
            if ((b.stringLiterals.contains(a) || b.stringConcatResults.contains(a))
                    && d.parameterType(i).descriptorString().startsWith("L")) {
                throw new JavaViolationException("Unsupported call: " + bareName);
            }
        }
    }

    /** Call-edge, demand, and purity bookkeeping for a call to a library contract member. */
    private void recordContractCall(ContractMember cm, AstNode<StmtExpr> call, String calleeKey) {
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(cm.calleeKey());
        }
        ctx.contracts.demand(cm);
        b.callNodes.add(call);
        b.calleeKeys.put(call.val(), calleeKey);
    }

    /** True iff {@code m}'s generic Signature result is a type variable; false without a Signature. */
    private static boolean returnsTypeVar(java.lang.classfile.MethodModel m) {
        var sa = m.findAttribute(java.lang.classfile.Attributes.signature()).orElse(null);
        if (sa == null) {
            return false;
        }
        try {
            return java.lang.classfile.MethodSignature.parseFrom(sa.signature().stringValue()).result()
                    instanceof java.lang.classfile.Signature.TypeVarSig;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Push a non-void call's result (typed from the erased descriptor), or
     * emit a void call as a statement. A result whose generic type is a type
     * variable ({@code resultIsTypeVar}) is recorded in
     * {@link BodyTranslator#typeVarValues}.
     */
    private void finishCall(AstNode<StmtExpr> call, java.lang.constant.MethodTypeDesc d,
                            boolean resultIsTypeVar) {
        String ret = d.returnType().descriptorString();
        if (ret.equals("V")) {
            b.addStmt(call.val());
        } else {
            b.stack.push(call);
            b.noteDesc(call, ret);
            if (ret.startsWith("L")) {
                b.staticTypeOf.put(call, TypeRef.ofDescriptor(ret));
            }
            if (resultIsTypeVar) {
                b.typeVarValues.add(call);
            }
        }
    }

    void invokeDynamic(java.lang.classfile.instruction.InvokeDynamicInstruction indy) {
        List<AstNode<StmtExpr>> captures = b.popArgs(indy.typeSymbol().parameterCount());
        try {
            var bsm = indy.invokedynamic().bootstrap();
            String bo = bsm.bootstrapMethod().reference().owner().asInternalName();
            if (bo.equals("java/lang/invoke/LambdaMetafactory") && bsm.arguments().size() >= 2
                    && bsm.arguments().get(1)
                            instanceof java.lang.classfile.constantpool.MethodHandleEntry mh) {
                var ref = mh.reference();
                String name = ref.nameAndType().name().stringValue();
                String desc = ref.nameAndType().type().stringValue();
                boolean isLambdaImpl = name.startsWith("lambda$") && b.cm != null;
                AstNode<StmtExpr> handle = b.push(new StmtExpr.LiteralString("$lambda"));
                b.lambdas.put(handle, new BodyTranslator.LambdaHandle(name, desc, captures.size(), !isLambdaImpl));
                return;
            }
            if (bo.equals("java/lang/invoke/StringConcatFactory")) {
                String iname = indy.name().stringValue();
                if (iname.equals("makeConcatWithConstants") && !bsm.arguments().isEmpty()
                        && bsm.arguments().get(0)
                                instanceof java.lang.classfile.constantpool.StringEntry recipe) {
                    b.stringConcatResults.add(b.push(stringConcat(recipe.stringValue(), captures).val()));
                    return;
                }
                if (iname.equals("makeConcat")) {
                    b.stringConcatResults.add(b.push(stringConcat(null, captures).val()));
                    return;
                }
            }
        } catch (RuntimeException ignore) {
            // fall through to the unsupported marker
        }
        b.todo("indy_" + indy.name().stringValue());
        b.push(LaurelFactory.var_("UNSUPPORTED_indy_"
                + indy.name().stringValue().replaceAll("[^A-Za-z0-9_]", "_")));
    }

    /** The recipe's `+` chain (old stringConcat -> convertBinary PLUS -> $add). */
    private AstNode<StmtExpr> stringConcat(String recipe, List<AstNode<StmtExpr>> args) {
        var parts = new ArrayList<AstNode<StmtExpr>>();
        if (recipe == null) {
            parts.addAll(args);
        } else {
            int argAt = 0;
            var sb = new StringBuilder();
            for (int i = 0; i < recipe.length(); i++) {
                char c = recipe.charAt(i);
                if (c == '\u0001') {
                    if (sb.length() > 0) {
                        parts.add(b.e(new StmtExpr.LiteralString(sb.toString())));
                        sb.setLength(0);
                    }
                    if (argAt < args.size()) {
                        parts.add(args.get(argAt++));
                    }
                } else {
                    sb.append(c);
                }
            }
            if (sb.length() > 0) {
                parts.add(b.e(new StmtExpr.LiteralString(sb.toString())));
            }
        }
        if (parts.isEmpty()) {
            return b.e(new StmtExpr.LiteralString(""));
        }
        AstNode<StmtExpr> acc = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            acc = b.e(LaurelFactory.primitiveOp(new Operation.Add(), acc, parts.get(i)));
        }
        return acc;
    }

    /** An inner-class ctor's enclosing-instance argument, or null. */
    private AstNode<StmtExpr> innerCtorOuter(String ownerInternal,
                                             java.lang.constant.MethodTypeDesc d,
                                             List<AstNode<StmtExpr>> args) {
        int dollar = ownerInternal.lastIndexOf('$');
        if (dollar < 0 || args.isEmpty() || d.parameterCount() == 0) {
            return null;
        }
        String enclosing = ownerInternal.substring(0, dollar);
        return d.parameterType(0).descriptorString().equals("L" + enclosing + ";")
                ? args.get(0) : null;
    }

    /** Drop the requireNonNull statement guarding a qualified inner allocation. */
    private void dropNullCheckOf(AstNode<StmtExpr> qualifier) {
        if (b.stmts.isEmpty()) {
            return;
        }
        AstNode<StmtExpr> last = b.stmts.get(b.stmts.size() - 1);
        if (last.val() instanceof StmtExpr.StaticCall sc
                && sc.callee().text().endsWith("_requireNonNull")
                && sc.arguments().size() == 1
                && LaurelTrees.structuralEquals(sc.arguments().get(0), qualifier)) {
            b.stmts.remove(b.stmts.size() - 1);
        }
    }
}
