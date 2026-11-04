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

        if (b.arrays.enabled()) {
            refuseArrayLibraryCall(ownerInternal, name);
        }
        if (isStatic && isJVerifyOwner(ownerInternal)) {
            b.specCalls.jverifyInvoke(inv, name, n, d);
            return;
        }

        List<AstNode<StmtExpr>> args = b.popArgs(n);
        // A lambda passed to a call is a VALUE (see BodyTranslator.materializeLambda) --
        // except on a throwsOn case chain, which consumes the raw handle as the
        // exceptional postcondition it is.
        boolean consumesHandle = isThrowsOnCaseOwner(ownerInternal);
        for (int i = 0; i < n; i++) {
            b.arrays.refuseAsObject(args.get(i), d.parameterType(i).descriptorString());
            var arg = consumesHandle ? args.get(i) : b.materializeLambda(args.get(i));
            args.set(i, b.coerceArg(arg, d.parameterType(i).descriptorString()));
        }

        if (isInit) {
            AstNode<StmtExpr> recv = b.stack.isEmpty() ? b.e(new StmtExpr.LiteralString("?")) : b.stack.pop();
            String g = b.pendingNew.get(recv);
            if (g == null && b.inCtor) {
                String ownerFqn = JavaNames.dottedFqn(ownerInternal);
                var ocm = ctx.resolver.programClassModels.get(ownerFqn);
                var target = ocm == null ? null : Constructors.find(ocm, d);
                bindConstructorCall(ownerFqn, ocm, target, d);
                if (target != null && Constructors.eligible(ocm, target)) {
                    String nm = Constructors.procName(ownerFqn, d);
                    ctx.constructorCalls.add(nm);
                    b.addStmt(new StmtExpr.InstanceCall(recv, LaurelFactory.ident(nm), args));
                }
                return;
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
            BoxModel.Wrapper box = BoxModel.wrapperOf(g);
            if (!ctx.contractMode && ctx.composites.boxes.models(box)) {
                if (d.parameterCount() != 1 || !d.parameterType(0).descriptorString().equals(box.primitiveDesc)) {
                    throw new JavaViolationException("constructor " + box.simpleName() + "("
                            + String.join(", ", d.parameterList().stream()
                                    .map(java.lang.constant.ClassDesc::displayName).toList())
                            + ") is not yet supported");
                }
                pushBox(box, args.get(0));
                return;
            }
            // Inner-class enclosing-instance argument: dropped from the
            // constructor arguments.
            AstNode<StmtExpr> outer = innerCtorOuter(g, d, args);
            List<AstNode<StmtExpr>> ctorArgs = args;
            if (outer != null) {
                ctorArgs = args.subList(1, args.size());
            }
            // Allocation: refused in a contract (checked at
            // spec-consumption), the sort registered, ctor args
            // translated-and-discarded.
            TypeRef t = TypeRef.ofClassInternal(g, false, false);
            String sortName = ctx.resolver.qualifyTypeName(t.nameWithScope(), t.fqnFromContext());
            ctx.composites.referencedCompositeTypes.add(sortName);
            String ctorFqn = JavaNames.dottedFqn(g);
            var ocm = ctx.resolver.programClassModels.get(ctorFqn);
            bindConstructorCall(ctorFqn, ocm, ocm == null ? null : Constructors.find(ocm, d), d);
            var target = ocm == null || outer != null ? null : Constructors.find(ocm, d);
            if (target != null && Constructors.eligible(ocm, target)) {
                String nm = Constructors.procName(ctorFqn, d);
                ctx.constructorCalls.add(nm);
                String tmp = "new$" + (++b.synthCounter);
                var alloc = b.e(new StmtExpr.New(LaurelFactory.ident(sortName),
                        newTypeArgs(sortName, d, ctorArgs)));
                var decl = b.e(new StmtExpr.Assign(List.of(new AstNode<org.strata.jverify.laurel.Variable>(
                        new org.strata.jverify.laurel.Variable.Declare(new org.strata.jverify.laurel.ParameterOpt(
                                LaurelFactory.ident(tmp), Optional.empty())), SourceMap.UNKNOWN_RANGE)), alloc));
                var init = b.e(new StmtExpr.InstanceCall(b.e(LaurelFactory.var_(tmp)), LaurelFactory.ident(nm),
                        ctorArgs));
                AstNode<StmtExpr> nw = b.push(new StmtExpr.Block(List.of(decl, init, b.e(LaurelFactory.var_(tmp))),
                        Optional.empty()));
                b.callNodes.add(nw);
                b.staticTypeOf.put(nw, t);
                return;
            }
            AstNode<StmtExpr> nw = b.push(new StmtExpr.New(LaurelFactory.ident(sortName),
                    newTypeArgs(sortName, d, ctorArgs)));
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
        b.arrays.refuseAsObject(recv, null);
        instanceInvoke(recv, ownerInternal, name, args, d);
    }

    /**
     * The array operations outside the model, refused before any lookup:
     * {@code System.arraycopy}, every {@code java.util.Arrays} method, and a
     * method of an array class ({@code clone()}; any other is an Object use).
     */
    private static void refuseArrayLibraryCall(String ownerInternal, String name) {
        if (ownerInternal.equals("java/lang/System") && name.equals("arraycopy")) {
            throw new JavaViolationException(ArrayModel.ARRAYCOPY);
        }
        if (ownerInternal.equals("java/util/Arrays")) {
            throw new JavaViolationException(ArrayModel.ARRAYS_UTIL);
        }
        if (ownerInternal.startsWith("[")) {
            throw new JavaViolationException(name.equals("clone") ? ArrayModel.CLONE : ArrayModel.AS_OBJECT);
        }
    }

    private void bindConstructorCall(String ownerFqn, java.lang.classfile.ClassModel ocm,
                                     java.lang.classfile.MethodModel target, java.lang.constant.MethodTypeDesc d) {
        if (target == null) {
            return;
        }
        refuseOptedOutCallee(ocm, target, "<init>");
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(ownerFqn + "#" + Constructors.procName(ownerFqn, d));
        }
    }

    private void refuseOptedOutCallee(java.lang.classfile.ClassModel cm, java.lang.classfile.MethodModel m,
                                      String bareName) {
        if (ctx.resolver.isOptedOut(cm, m) && !isPureCallee(m) && !framelessWithoutContract(m)) {
            throw new JavaViolationException(
                    "call to '" + bareName + "', which could not be translated");
        }
    }

    private boolean isJVerifyOwner(String ownerInternal) {
        return ownerInternal.equals("org/strata/jverify/JVerify")
                || ownerInternal.equals("org/strata/jverify/Verify");
    }

    private boolean isThrowsOnCaseOwner(String ownerInternal) {
        return ownerInternal.endsWith("/ThrowsOnCase") || ownerInternal.endsWith("$ThrowsOnCase");
    }

    /**
     * The Laurel type arguments for a {@code new} of {@code sortName}, empty when it
     * is not a generic program composite.
     *
     * <p>Solved from the constructor's generic signature against the actual argument
     * types ({@link ClassResolver#inferNewTypeArgs}), which is javac's own diamond
     * inference run back-to-front. A slot the arguments leave undetermined -- a
     * no-argument constructor, or a type parameter the constructor never mentions --
     * falls back to {@code Object}: the erasure-equivalent instantiation, which keeps
     * the emitted Laurel well formed. That is the one lossy case, and it only costs
     * precision where nothing in the bytecode records the argument at all.
     */
    private List<org.strata.jverify.laurel.AstNode<HighType>> newTypeArgs(
            String sortName, java.lang.constant.MethodTypeDesc ctorDesc,
            List<AstNode<StmtExpr>> ctorArgs) {
        if (ctx.resolver.classTypeParams(sortName).isEmpty()) {
            return List.of();
        }
        var argTypes = new java.util.ArrayList<String>();
        for (var a : ctorArgs) {
            TypeRef at = b.staticTypeOf.get(a);
            argTypes.add(at == null ? null : at.display());
        }
        var solved = ctx.resolver.inferNewTypeArgs(sortName, ctorDesc, argTypes);
        // A no-argument constructor determines nothing, so fall back to the
        // instantiation the DESTINATION declares. That is the other place the
        // classfile keeps it: the LocalVariableTypeTable entry of the local this
        // allocation is stored into (GenericNoArgConstructor).
        List<String> fromDest = solved.stream().anyMatch(java.util.Objects::isNull)
                ? destinationTypeArgs(sortName, solved.size())
                : List.of();
        var out = new java.util.ArrayList<org.strata.jverify.laurel.AstNode<HighType>>();
        for (int i = 0; i < solved.size(); i++) {
            String rendered = solved.get(i);
            if (rendered == null && i < fromDest.size()) {
                rendered = fromDest.get(i);
            }
            if (rendered == null) {
                // Nothing in the classfile records this argument. Object is the
                // erasure-equivalent instantiation, which keeps the emitted Laurel
                // well formed; it is only reached for a type parameter that neither
                // the constructor nor the destination mentions.
                rendered = "Object";
            }
            out.add(LaurelFactory.node(TypeRef.ofRendered(TypeRef.readType(rendered), false, false)
                    .toLaurel(ctx.typeParams, ctx.methodTypeParams,
                            ctx.resolver, ctx.composites)));
        }
        return out;
    }

    /**
     * The type arguments of the nearest FOLLOWING local declaration whose generic
     * type is a parameterisation of {@code sortName} -- the local this allocation is
     * about to be stored into. Scope positions are sequence indices, so "following"
     * is measured against the translator's current position. Empty when no such
     * local exists or its arity does not match.
     */
    private List<String> destinationTypeArgs(String sortName, int arity) {
        if (b.locals == null) {
            return List.of();
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (var scopes : b.locals.bySlot.values()) {
            for (var s : scopes) {
                if (s.genericType() == null) {
                    continue;
                }
                var declared = TypeRef.ofRendered(s.genericType(), false, false);
                if (declared.kind() != TypeRef.Kind.CLASS
                        || !ctx.resolver.qualifyTypeName(declared.nameWithScope(),
                                s.desc() == null ? null : TypeRef.ofDescriptor(s.desc()).fqnFromContext()).equals(sortName)) {
                    continue;
                }
                int distance = s.start() - b.pos;
                if (distance >= 0 && distance < bestDistance) {
                    bestDistance = distance;
                    best = s.genericType();
                }
            }
        }
        var args = best == null ? List.<String>of() : TypeRef.typeArguments(best);
        return args.size() == arity ? args : List.of();
    }

    private List<AstNode<StmtExpr>> unifyTypeVariableArgs(java.lang.classfile.MethodModel callee,
                                                         List<AstNode<StmtExpr>> args) {
        var sig = callee.findAttribute(java.lang.classfile.Attributes.signature()).map(sa -> {
            try {
                return java.lang.classfile.MethodSignature.parseFrom(sa.signature().stringValue());
            } catch (RuntimeException e) {
                return null;
            }
        }).orElse(null);
        if (sig == null || sig.typeParameters().isEmpty() || sig.arguments().size() != args.size()) {
            return args;
        }
        var groups = new HashMap<String, List<Integer>>();
        for (int i = 0; i < args.size(); i++) {
            if (sig.arguments().get(i) instanceof java.lang.classfile.Signature.TypeVarSig tv) {
                groups.computeIfAbsent(tv.identifier(), k -> new ArrayList<>()).add(i);
            }
        }
        var out = new ArrayList<>(args);
        for (var idx : groups.values()) {
            var types = new java.util.HashSet<String>();
            for (int i : idx) {
                TypeRef t = b.staticTypeOf.get(args.get(i));
                types.add(t == null ? "?" : String.valueOf(b.resolveFqn(t)));
            }
            if (types.size() < 2) {
                continue;
            }
            String erased = callee.methodTypeSymbol().parameterType(idx.get(0)).descriptorString();
            if (!erased.startsWith("L")) {
                continue;
            }
            String sort = CompositeModel.sortNameForFqn(JavaNames.dottedFqn(erased.substring(1, erased.length() - 1)));
            for (int i : idx) {
                String tmp = "up$" + (++b.synthCounter);
                var decl = b.e(new StmtExpr.Assign(List.of(new AstNode<Variable>(new Variable.Declare(
                        new ParameterOpt(LaurelFactory.ident(tmp), Optional.of(LaurelFactory.node(
                                new HighType.UserDefined(LaurelFactory.ident(sort)))))),
                        SourceMap.UNKNOWN_RANGE)), args.get(i)));
                out.set(i, b.e(new StmtExpr.Block(List.of(decl, b.e(LaurelFactory.var_(tmp))), Optional.empty())));
            }
        }
        return out;
    }

    private boolean contractParamsMatch(List<java.lang.constant.ClassDesc> contractParams,
                                        List<java.lang.constant.ClassDesc> callParams) {
        if (contractParams.size() != callParams.size()) {
            return false;
        }
        for (int i = 0; i < contractParams.size(); i++) {
            var c = contractParams.get(i);
            if (c.equals(callParams.get(i))) {
                continue;
            }
            if (!c.isClassOrInterface() || !callParams.get(i).isClassOrInterface()) {
                return false;
            }
            String cFqn = JavaNames.dottedFqn(c.descriptorString().substring(1, c.descriptorString().length() - 1));
            var lib = ctx.resolver.libraryContracts.entrySet().stream()
                    .filter(e -> e.getValue().fqn().equals(cFqn)).map(java.util.Map.Entry::getKey).findFirst();
            String callFqn = JavaNames.dottedFqn(callParams.get(i).descriptorString()
                    .substring(1, callParams.get(i).descriptorString().length() - 1));
            if (lib.isEmpty() || !lib.get().equals(callFqn)) {
                return false;
            }
        }
        return true;
    }

    private boolean missingContractStatic(String ownerInternal, String bareName, List<AstNode<StmtExpr>> args,
                                          java.lang.constant.MethodTypeDesc d) {
        String ownerFqn = JavaNames.dottedFqn(ownerInternal);
        if (ownerInternal.startsWith("org/strata/jverify/")) {
            return false;
        }
        var sb = new StringBuilder(JavaNames.staticProcedure(ownerFqn, bareName));
        for (var pt : d.parameterList()) {
            sb.append(ClassResolver.MANGLE_SEPARATOR).append(ClassResolver.erasedTypeName(pt));
        }
        String procName = sb.toString();
        if (!ctx.missingContractProcs.containsKey(procName)) {
            var inputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
            var outputs = new ArrayList<org.strata.jverify.laurel.Parameter>();
            try {
                for (int i = 0; i < d.parameterCount(); i++) {
                    inputs.add(new org.strata.jverify.laurel.Parameter(LaurelFactory.ident("p" + i),
                            LaurelFactory.node(TypeRef.ofDescriptor(d.parameterType(i).descriptorString())
                                    .toLaurel(java.util.Set.of(), ctx.resolver, ctx.composites))));
                }
                if (!d.returnType().descriptorString().equals("V")) {
                    outputs.add(new org.strata.jverify.laurel.Parameter(
                            LaurelFactory.reservedIdent(LaurelFactory.LAUREL_RESULT_BINDING),
                            LaurelFactory.node(TypeRef.ofDescriptor(d.returnType().descriptorString())
                                    .toLaurel(java.util.Set.of(), ctx.resolver, ctx.composites))));
                }
            } catch (JavaViolationException e) {
                return false;
            }
            var posts = new ArrayList<org.strata.jverify.laurel.Condition>();
            if (!outputs.isEmpty()) {
                var callArgs = new ArrayList<AstNode<StmtExpr>>();
                for (var in : inputs) {
                    callArgs.add(LaurelFactory.noRange(new StmtExpr.Var(new Variable.Local(in.name()))));
                }
                var eq = LaurelFactory.primitiveOp(new Operation.Eq(),
                        LaurelFactory.noRange(LaurelFactory.reservedVar_(LaurelFactory.LAUREL_RESULT_BINDING)),
                        LaurelFactory.noRange(new StmtExpr.StaticCall(LaurelFactory.ident(procName), callArgs, List.of())));
                posts.add(new org.strata.jverify.laurel.Condition(LaurelFactory.noRange(eq), Optional.empty(),
                        new org.strata.jverify.laurel.ConditionMode.Both()));
            }
            ctx.missingContractProcs.put(procName, new org.strata.jverify.laurel.Procedure(
                    LaurelFactory.ident(procName), List.of(), inputs, outputs, List.of(),
                    new org.strata.jverify.laurel.CoroutineContracts.Regular(), Optional.empty(),
                    new org.strata.jverify.laurel.Body.Opaque(posts, Optional.empty(),
                            d.returnType().descriptorString().equals("V")
                                    ? LaurelFactory.unguardedModifies(List.of(LaurelFactory.noRange(new StmtExpr.All())))
                                    : List.of()),
                    Optional.empty(), false, List.of(), Optional.empty(), Optional.empty(), false, List.of(),
                    List.of(), List.of()));
            if (!ctx.method.info.quiet) {
                ctx.reporter.reportWarning(ctx.src.uri, ctx.src.positionRange(b.stmtRange(b.pos, false)),
                        "missing contract for method '" + bareName + "' in class '" + ownerFqn + "'");
            }
        }
        for (int i = 0; i < args.size(); i++) {
            args.set(i, b.coerceArg(args.get(i), d.parameterType(i).descriptorString()));
        }
        AstNode<StmtExpr> call = b.e(new StmtExpr.StaticCall(LaurelFactory.ident(procName), args, List.of()));
        b.callNodes.add(call);
        if (d.returnType().descriptorString().equals("V")) {
            b.calleeKeys.put(call.val(), procName);
        }
        finishCall(call, d);
        return true;
    }

    private static boolean framelessWithoutContract(java.lang.classfile.MethodModel m) {
        var code = m.code().orElse(null);
        if (code == null) {
            return false;
        }
        for (var el : code) {
            switch (el) {
                case java.lang.classfile.instruction.FieldInstruction fi -> {
                    if (fi.opcode() == java.lang.classfile.Opcode.PUTFIELD
                            || fi.opcode() == java.lang.classfile.Opcode.PUTSTATIC) {
                        return false;
                    }
                }
                case java.lang.classfile.instruction.ArrayStoreInstruction as -> {
                    return false;
                }
                case java.lang.classfile.instruction.ReturnInstruction ri -> {
                    return false;
                }
                case java.lang.classfile.instruction.InvokeDynamicInstruction indy -> {
                    return false;
                }
                case java.lang.classfile.instruction.InvokeInstruction inv -> {
                    if (inv.name().stringValue().equals("<init>")) {
                        continue;
                    }
                    return false;
                }
                default -> {
                }
            }
        }
        return true;
    }

    private static boolean isPureCallee(java.lang.classfile.MethodModel m) {
        return Declarations.hasAnnotation(m, "Transparent");
    }

    /** {@code Integer.valueOf(int)} and its siblings: javac's autoboxing of one primitive. */
    private static boolean isPrimitiveBoxing(String ownerInternal, String name,
                                             java.lang.constant.MethodTypeDesc d) {
        return name.equals("valueOf") && d.parameterCount() == 1 && d.parameterType(0).isPrimitive()
                && switch (ownerInternal) {
                    case "java/lang/Integer", "java/lang/Long", "java/lang/Short", "java/lang/Byte",
                         "java/lang/Character", "java/lang/Boolean" -> true;
                    default -> false;
                };
    }

    /**
     * Push the box of {@code value} ({@link BoxModel#box}). The value it boxes
     * is recorded in {@link BodyTranslator#boxedValue}, so a consumer that
     * needs the primitive (an unboxing, {@code equals}, preconditionOf)
     * unwraps it.
     */
    private void pushBox(BoxModel.Wrapper w, AstNode<StmtExpr> value) {
        AstNode<StmtExpr> box = b.push(ctx.composites.boxes.box(w, value));
        b.boxedValue.put(box, value);
        b.callNodes.add(box);
        b.noteDesc(box, w.descriptor());
        b.staticTypeOf.put(box, TypeRef.ofDescriptor(w.descriptor()));
    }

    /** Static user call. */
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
                String procName = JavaNames.staticProcedure(cm.sortOrFqn(), cm.laurelName());
                AstNode<StmtExpr> call = b.e(new StmtExpr.StaticCall(LaurelFactory.ident(procName), args, List.of()));
                recordContractCall(cm, call, cm.sortOrFqn() + "_" + cm.laurelName());
                finishCall(call, d, null, cm.returnsTypeVar());
                return;
            }
        }
        TypeRef ownerRef = TypeRef.ofClassInternal(ownerInternal, false, false);
        String fqn = ctx.resolver.knownClasses.get(ownerRef.nameWithScope());
        if (fqn == null && isPrimitiveBoxing(ownerInternal, bareName, d) && !ctx.contractMode
                && ctx.composites.boxes.models(BoxModel.wrapperOf(ownerInternal))) {
            pushBox(BoxModel.wrapperOf(ownerInternal), args.get(0));
            return;
        }
        if (fqn == null && isPrimitiveBoxing(ownerInternal, bareName, d)) {
            AstNode<StmtExpr> marker = b.push(new StmtExpr.LiteralString("$box"));
            b.boxedValue.put(marker, args.get(0));
            b.noteDesc(marker, d.returnType().descriptorString());
            b.defer(marker, "Call to unknown class '" + ownerRef.nameWithScope() + "' is not supported");
            return;
        }
        if (fqn == null) {
            var libContract = ctx.resolver.libraryContracts.get(JavaNames.dottedFqn(ownerInternal));
            if (libContract != null) {
                fqn = libContract.fqn();
            }
        }
        if (fqn == null && missingContractStatic(ownerInternal, bareName, args, d)) {
            return;
        }
        if (fqn == null) {
            throw new JavaViolationException(
                    "Call to unknown class '" + ownerRef.nameWithScope() + "' is not supported");
        }
        var staticCm = ctx.resolver.programClassModels.get(fqn);
        // The overload javac bound is the one whose descriptor is on the invoke,
        // so the suffix is computed from it -- declaration and call site agree.
        String mangled = bareName;
        java.lang.classfile.MethodModel bound = null;
        if (staticCm != null) {
            for (var m : staticCm.methods()) {
                if (m.methodName().stringValue().equals(bareName)
                        && (m.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_SYNTHETIC
                                | java.lang.classfile.ClassFile.ACC_BRIDGE)) == 0
                        && (m.flags().flagsMask() & java.lang.classfile.ClassFile.ACC_STATIC) != 0
                        && m.methodTypeSymbol().parameterList().equals(d.parameterList())) {
                    mangled = bareName + ctx.resolver.overloadSuffix(fqn, m);
                    bound = m;
                    refuseOptedOutCallee(staticCm, m, bareName);
                }
            }
        }
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(fqn + "#" + mangled);
        }
        if (bound != null) {
            args = unifyTypeVariableArgs(bound, args);
        }
        AstNode<StmtExpr> call = b.e(new StmtExpr.StaticCall(
                LaurelFactory.ident(JavaNames.staticProcedure(fqn, mangled)), args, List.of()));
        b.callNodes.add(call);
        // An unresolved (library) callee gets a key no purity entry matches, so it
        // reads as impure -- the conservative side of the heap-write fallback.
        b.calleeKeys.put(call.val(), bound != null
                ? Declarations.purityKey(fqn, bound)
                : fqn + "_" + bareName);
        if (bound != null) {
            b.calleeModels.put(call.val(), new BodyTranslator.CalleeRef(fqn, bound));
        }
        finishCall(call, d, bound, bound != null && returnsTypeVar(bound));
    }

    /**
     * A call on a receiver whose type is a LIBRARY type with a @Contract in this
     * program: bound to the contract's method, which BytecodeToLaurelCompiler emits as
     * a procedure of the library type's sort. The method
     * is matched by name and erased descriptor. Returns whether the call was handled.
     */
    private boolean libraryContractInvoke(AstNode<StmtExpr> recv, String receiverFqn, String bareName,
                                          List<AstNode<StmtExpr>> args, java.lang.constant.MethodTypeDesc d) {
        var contract = ctx.resolver.libraryContractOf(receiverFqn);
        if (contract == null) {
            contract = ctx.resolver.libraryContracts.values().stream()
                    .filter(c -> c.fqn().equals(receiverFqn)).findFirst().orElse(null);
        }
        if (contract == null) {
            return false;
        }
        java.lang.classfile.MethodModel target = null;
        for (var mm : contract.decl().cm().methods()) {
            if (mm.methodName().stringValue().equals(bareName)
                    && contractParamsMatch(mm.methodTypeSymbol().parameterList(), d.parameterList())
                    && (mm.flags().flagsMask() & (java.lang.classfile.ClassFile.ACC_STATIC
                            | java.lang.classfile.ClassFile.ACC_SYNTHETIC
                            | java.lang.classfile.ClassFile.ACC_BRIDGE)) == 0) {
                target = mm;
            }
        }
        String sort = ctx.resolver.libraryContractSort(
                ctx.resolver.contracteeFqnOfLibraryContract(contract));
        if (target == null && bareName.equals("equals") && d.descriptorString().equals("(Ljava/lang/Object;)Z")) {
            var procs = ctx.composites.instanceProcsBySort.computeIfAbsent(sort, k -> new ArrayList<>());
            if (procs.stream().noneMatch(p -> p.name().text().equals("equals"))) {
                ctx.composites.referencedCompositeTypes.add(sort);
                ctx.composites.referencedCompositeTypes.add(CompositeModel.OBJECT_SORT);
                procs.add(new org.strata.jverify.laurel.Procedure(LaurelFactory.ident("equals"), List.of(),
                        List.of(new org.strata.jverify.laurel.Parameter(LaurelFactory.ident(LaurelFactory.LAUREL_SELF_BINDING),
                                        LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(sort)))),
                                new org.strata.jverify.laurel.Parameter(LaurelFactory.ident("obj"),
                                        LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(CompositeModel.OBJECT_SORT))))),
                        List.of(new org.strata.jverify.laurel.Parameter(
                                LaurelFactory.reservedIdent(LaurelFactory.LAUREL_RESULT_BINDING),
                                LaurelFactory.node(new HighType.TBool()))),
                        List.of(), new org.strata.jverify.laurel.CoroutineContracts.Regular(), Optional.empty(),
                        new org.strata.jverify.laurel.Body.Opaque(List.of(), Optional.empty(), List.of()),
                        Optional.empty(), false, List.of(), Optional.empty(), Optional.empty(), false, List.of(),
                        List.of(), List.of()));
            }
            AstNode<StmtExpr> call = b.e(new StmtExpr.InstanceCall(recv, LaurelFactory.ident("equals"), args));
            b.callNodes.add(call);
            finishCall(call, d);
            return true;
        }
        if (target == null) {
            return false;
        }
        String mangled = bareName + ctx.resolver.overloadSuffix(contract.fqn(), target);
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(sort + "#" + mangled);
        }
        AstNode<StmtExpr> call = b.e(new StmtExpr.InstanceCall(recv, LaurelFactory.ident(mangled), args));
        b.callNodes.add(call);
        b.calleeKeys.put(call.val(), Declarations.purityKey(contract.fqn(), target));
        finishCall(call, d);
        return true;
    }

    /** Instance user call (value receiver). */
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
                b.refuseAllocTempMention(args.get(0));
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
        BoxModel.Wrapper box = BoxModel.wrapperOf(ownerInternal);
        if (!ctx.contractMode && ctx.composites.boxes.models(box) && boxMember(box, recv, bareName, args, d)) {
            return;
        }
        // String literal receiver: a literal is a Laurel string, not the
        // String composite, so the call always refuses. Warn when no contract
        // member matches, then check the args (an array-initializer argument
        // reports its own refusal first: argument refusals precede the
        // receiver refusal, pinned by the corpus), then refuse.
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
        if (receiverFqn != null && (!ctx.resolver.programClassModels.containsKey(receiverFqn)
                        || ctx.resolver.libraryContracts.values().stream().anyMatch(c -> c.fqn().equals(receiverFqn)))
                && libraryContractInvoke(recv, receiverFqn, bareName, args, d)) {
            return;
        }
        ContractMember member = contractMemberFor(recv, receiverFqn, ownerInternal, bareName, args, d);
        if (member != null) {
            refuseStringValuedArguments(bareName, args, d);
            AstNode<StmtExpr> call = b.e(new StmtExpr.InstanceCall(
                    recv, LaurelFactory.ident(member.laurelName()), args));
            recordContractCall(member, call, member.sortOrFqn() + "_" + member.laurelName());
            finishCall(call, d, null, member.returnsTypeVar());
            return;
        }
        if (receiverFqn == null || !ctx.resolver.programClassModels.containsKey(receiverFqn)) {
            throw new JavaViolationException("call to '" + bareName
                    + "' on a receiver whose static type is not a class is not yet supported");
        }
        var callee = ctx.resolver.findCallee(receiverFqn, bareName, d);
        if (callee == null) {
            throw new JavaViolationException("call to library method '" + bareName + "' is not yet supported");
        }
        refuseOptedOutCallee(ctx.resolver.programClassModels.get(callee.declaringFqn()), callee.method(), bareName);
        // Mangled from the DECLARING class's closure, matching the declaration.
        String mangled = bareName
                + ctx.resolver.overloadSuffix(callee.declaringFqn(), callee.method());
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
            ctx.method.info.callees.add(callee.declaringFqn() + "#" + mangled);
        }
        AstNode<StmtExpr> call = b.e(new StmtExpr.InstanceCall(recv, LaurelFactory.ident(mangled), args));
        b.callNodes.add(call);
        b.calleeKeys.put(call.val(), Declarations.purityKey(callee.declaringFqn(), callee.method()));
        b.calleeModels.put(call.val(), new BodyTranslator.CalleeRef(callee.declaringFqn(), callee.method()));
        finishCall(call, callee.method().methodTypeSymbol(), callee.method(), returnsTypeVar(callee.method()));
    }

    /**
     * A call of a modelled wrapper class's own method ({@link BoxModel}): its
     * unboxing method, and {@code equals(Object)}. Any other method misses.
     *
     * @return whether the call was translated
     */
    private boolean boxMember(BoxModel.Wrapper w, AstNode<StmtExpr> recv, String bareName,
                              List<AstNode<StmtExpr>> args, java.lang.constant.MethodTypeDesc d) {
        if (bareName.equals(w.unboxName) && d.parameterCount() == 0) {
            AstNode<StmtExpr> value = unboxed(w, recv);
            b.stack.push(value);
            b.noteDesc(value, w.primitiveDesc);
            return true;
        }
        if (bareName.equals("equals") && d.descriptorString().equals("(Ljava/lang/Object;)Z")) {
            b.noteDesc(b.push(boxEquals(w, recv, args.get(0))), "Z");
            return true;
        }
        return false;
    }

    /**
     * The primitive {@code n} boxes: the boxed value itself when {@code n} is
     * a box this method just made ({@link BodyTranslator#boxedValue}), so
     * {@code Integer.valueOf(x).intValue()} allocates nothing, else a read of
     * its value field.
     */
    private AstNode<StmtExpr> unboxed(BoxModel.Wrapper w, AstNode<StmtExpr> n) {
        AstNode<StmtExpr> value = b.boxedValue.get(n);
        if (value != null && w.descriptor().equals(b.descOf.get(n))) {
            return value;
        }
        return b.e(ctx.composites.boxes.unbox(w, n));
    }

    /**
     * {@code recv.equals(arg)} on a box: the values compare equal when
     * {@code arg} is a box of the same class, and the result is false for any
     * other object. An argument statically typed as that class is such a box
     * (a Laurel composite value is never null). Otherwise its class is tested,
     * which reads it twice, so it must be a local.
     */
    private StmtExpr boxEquals(BoxModel.Wrapper w, AstNode<StmtExpr> recv, AstNode<StmtExpr> arg) {
        var boxes = ctx.composites.boxes;
        AstNode<StmtExpr> mine = unboxed(w, recv);
        if (w.descriptor().equals(b.descOf.get(arg))) {
            return LaurelFactory.primitiveOp(new Operation.Eq(), mine, unboxed(w, arg));
        }
        if (!(arg.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Local)) {
            throw new JavaViolationException("'" + w.simpleName()
                    + ".equals' on an argument that is not a local variable is not yet supported");
        }
        var type = LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(boxes.declare(w))));
        AstNode<StmtExpr> theirs = b.e(boxes.unbox(w, b.e(new StmtExpr.AsType(arg, type))));
        return new StmtExpr.IfThenElse(b.e(new StmtExpr.IsType(arg, type)),
                b.e(LaurelFactory.primitiveOp(new Operation.Eq(), mine, theirs)),
                Optional.of(b.e(new StmtExpr.LiteralBool(false))));
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

    private void finishCall(AstNode<StmtExpr> call, java.lang.constant.MethodTypeDesc d) {
        finishCall(call, d, null, false);
    }

    /**
     * Push a non-void call's result (typed from the erased descriptor), or
     * emit a void call as a statement. A result whose generic type is a type
     * variable ({@code resultIsTypeVar}) is recorded in
     * {@link BodyTranslator#typeVarValues} and {@link BodyTranslator#erasedResults}.
     */
    private void finishCall(AstNode<StmtExpr> call, java.lang.constant.MethodTypeDesc d,
                            java.lang.classfile.MethodModel callee, boolean resultIsTypeVar) {
        String ret = d.returnType().descriptorString();
        if (ret.equals("V")) {
            b.addStmt(call.val());
        } else {
            b.stack.push(call);
            b.noteDesc(call, ret);
            if (ret.startsWith("L") || (ret.startsWith("[") && ctx.composites.arrays.enabled())) {
                b.staticTypeOf.put(call, TypeRef.ofDescriptor(ret));
            }
            if (resultIsTypeVar) {
                b.typeVarValues.add(call);
                b.erasedResults.add(call.val());
                if (passesModelledBox(call.val())) {
                    b.erasedBoxResults.add(call.val());
                }
            }
        }
    }

    /** Whether {@code call} is passed a modelled box as an argument or as its receiver. */
    private boolean passesModelledBox(StmtExpr call) {
        List<AstNode<StmtExpr>> operands = new ArrayList<>();
        if (call instanceof StmtExpr.StaticCall sc) {
            operands.addAll(sc.arguments());
        } else if (call instanceof StmtExpr.InstanceCall ic) {
            operands.add(ic.target());
            operands.addAll(ic.arguments());
        }
        return operands.stream().anyMatch(o -> b.isModelledBox(o) || b.erasedBoxResults.contains(o.val()));
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
                String ret = indy.typeSymbol().returnType().descriptorString();
                String sam = ret.startsWith("L") ? JavaNames.dottedFqn(ret.substring(1, ret.length() - 1)) : null;
                // LambdaMetafactory's static arguments: the SAM's erased type, the
                // impl handle, and the SAM's type as this call site instantiates it.
                String samDesc = bsm.arguments().get(0)
                        instanceof java.lang.classfile.constantpool.MethodTypeEntry st
                        ? st.descriptor().stringValue() : null;
                String instantiatedDesc = bsm.arguments().size() >= 3 && bsm.arguments().get(2)
                        instanceof java.lang.classfile.constantpool.MethodTypeEntry it
                        ? it.descriptor().stringValue() : null;
                b.lambdas.put(handle, new BodyTranslator.LambdaHandle(name, desc, captures.size(),
                        !isLambdaImpl, sam, ref.owner().asInternalName(),
                        mh.kind() == java.lang.invoke.MethodHandleInfo.REF_invokeStatic,
                        indy.name().stringValue(), samDesc, instantiatedDesc));
                return;
            }
            if (bo.equals("java/lang/invoke/StringConcatFactory")) {
                for (var capture : captures) {
                    b.arrays.refuseAsObject(capture, null);
                }
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
        } catch (JavaViolationException e) {
            throw e;
        } catch (RuntimeException ignore) {
            // fall through to the unsupported marker
        }
        b.todo("indy_" + indy.name().stringValue());
        b.push(LaurelFactory.var_("UNSUPPORTED_indy_"
                + indy.name().stringValue().replaceAll("[^A-Za-z0-9_]", "_")));
    }

    /** The recipe's `+` chain, lowered to {@code $add}. */
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
}
