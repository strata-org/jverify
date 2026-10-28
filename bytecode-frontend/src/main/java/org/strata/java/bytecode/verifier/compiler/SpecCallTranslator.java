package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import org.strata.java.bytecode.frontend.ClassfileQueries;
import org.strata.java.bytecode.frontend.ControlFlowGraph;
import org.strata.java.bytecode.frontend.Locals;
import org.strata.java.bytecode.frontend.StructException;
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
 * Translates JVerify static spec/assertion calls: header markers
 * (precondition/postcondition/invariant/reads/modifies/decreases/throwsOn),
 * check/assume, quantifiers over lambda bodies, lambda-body translation with
 * parameter renames, and the contract-argument subtree checks.
 */
final class SpecCallTranslator {

    private final BodyTranslator b;
    private final TranslationContext ctx;

    SpecCallTranslator(BodyTranslator b) {
        this.b = b;
        this.ctx = b.ctx;
    }

    /** JVerify static spec/assertion calls (assertion arms + header spec markers). */
    static final String ABSTRACT_MARKER = "$isAbstract";

    void jverifyInvoke(java.lang.classfile.instruction.InvokeInstruction inv,
                               String name, int argc,
                               java.lang.constant.MethodTypeDesc d) {
        List<AstNode<StmtExpr>> args = b.popArgs(argc);
        boolean isVoid = d.returnType().descriptorString().equals("V");
        switch (name) {
            case "check" -> {
                if (argc != 1) {
                    throw new JavaViolationException("check should have a single argument");
                }
                b.addStmt(new StmtExpr.Assert(b.coerce(args.get(0), "Z"), Optional.empty()));
            }
            case "assume" -> {
                if (argc != 1) {
                    throw new JavaViolationException("assume should have a single argument");
                }
                b.addStmt(new StmtExpr.Assume(b.coerce(args.get(0), "Z")));
            }
            case "isAbstract", "isAbstractBoolean" -> b.push(new StmtExpr.LiteralString(ABSTRACT_MARKER));
            case "cast" -> {
                if (argc != 1) {
                    throw new JavaViolationException("cast should have a single argument");
                }
                b.stack.push(args.get(0));
                b.staticTypeOf.put(args.get(0), TypeRef.ofDescriptor(d.returnType().descriptorString()));
            }
            case "implies" -> {
                if (argc != 2) {
                    throw new JavaViolationException("implies should have two arguments");
                }
                if (ctx.composites.arrays.enabled() && b.arrays.mentionsArrayCall(args.get(1))) {
                    // An array read's bounds precondition holds only under the
                    // antecedent: Laurel's implication guards it, `!a | b` does not.
                    AstNode<StmtExpr> r = b.push(LaurelFactory.primitiveOp(new Operation.Implies(),
                            b.coerce(args.get(0), "Z"), b.coerce(args.get(1), "Z")));
                    b.bools.add(r);
                    return;
                }
                var notA = LaurelFactory.primitiveOp(new Operation.Not(), b.coerce(args.get(0), "Z"));
                AstNode<StmtExpr> r = b.push(LaurelFactory.primitiveOp(new Operation.Or(),
                        b.e(notA), b.coerce(args.get(1), "Z")));
                b.bools.add(r);
            }
            case "jequals" -> {
                if (argc != 2) {
                    throw new JavaViolationException("jequals should have two arguments");
                }
                b.arrays.refuseAsObject(args.get(0), null);
                b.arrays.refuseAsObject(args.get(1), null);
                AstNode<StmtExpr> r = b.push(LaurelFactory.primitiveOp(new Operation.Eq(),
                        args.get(0), args.get(1)));
                b.bools.add(r);
            }
            case "forall", "exists" -> {
                BodyTranslator.LambdaHandle lambda = argc == 1 ? b.lambdas.get(args.get(0)) : null;
                if (lambda == null) {
                    throw new JavaViolationException(name + " requires a single lambda argument");
                }
                AstNode<StmtExpr> r = b.push(translateQuantifier(name, lambda));
                b.bools.add(r);
            }
            case "preconditionOf" -> {
                // The callee's preconditions, with its
                // parameters replaced by the call's arguments. The callee may not be
                // translated yet, so a placeholder stands in and is inlined after the
                // whole compilation (BytecodeToLaurelCompiler.inlinePreconditionOf).
                AstNode<StmtExpr> arg = argc == 1 ? args.get(0) : null;
                if (arg != null) {
                    arg = b.boxedValue.getOrDefault(arg, arg);
                }
                BodyTranslator.CalleeRef ref = arg != null ? b.calleeModels.get(arg.val()) : null;
                if (ref == null) {
                    if (!ctx.method.info.quiet) {
                        ctx.reporter.reportError(ctx.src.uri,
                                ctx.src.positionRange(b.stmtRange(b.pos, false)),
                                "preconditionOf argument must be a method call");
                    }
                    ctx.method.info.constructErrorReported = true;
                    AstNode<StmtExpr> t = b.push(new StmtExpr.LiteralBool(true));
                    b.bools.add(t);
                    break;
                }
                AstNode<StmtExpr> receiver = null;
                List<AstNode<StmtExpr>> callArgs;
                if (arg.val() instanceof StmtExpr.InstanceCall ic) {
                    receiver = ic.target();
                    callArgs = ic.arguments();
                } else {
                    callArgs = ((StmtExpr.StaticCall) arg.val()).arguments();
                }
                String id = "$preconditionOf$" + ctx.pendingPreconditionOf.size();
                ctx.pendingPreconditionOf.put(id, new TranslationContext.PendingPreconditionOf(
                        ref.declaringFqn(), ref.method(), receiver, callArgs));
                AstNode<StmtExpr> r = b.push(new StmtExpr.StaticCall(LaurelFactory.reservedIdent(id), List.of(), List.of()));
                b.bools.add(r);
            }
            case "throwsOn" -> {
                // The base of an exceptional-behavior chain: push a handle
                // that .postcondition/.modifies links extend.
                if (argc != 1) {
                    throw new JavaViolationException("A throwsOn call must have exactly one argument");
                }
                b.refuseAllocTempMention(args.get(0));
                AstNode<StmtExpr> handle = b.push(new StmtExpr.LiteralString("$throwsOn"));
                b.specStmts.put(handle, new BodyTranslator.SpecStmt("throwsOn", List.of(args.get(0)),
                        b.lambdas.get(args.get(0)), new ArrayList<>(), b.pos));
            }
            default -> {
                if (b.HEADER_SPEC_NAMES.contains(name) && isVoid) {
                    // Header spec statement: marker consumed by contract extraction.
                    AstNode<StmtExpr> arg = argc >= 1 ? args.get(0) : null;
                    args.forEach(b::refuseAllocTempMention);
                    AstNode<StmtExpr> raw = b.e(new StmtExpr.LiteralString("$spec$" + name));
                    FileRange range = b.stmtRange(b.pos,
                            arg != null && b.lambdas.containsKey(arg));
                    AstNode<StmtExpr> placed = ctx.placeStatement(raw, range);
                    b.stmts.add(placed);
                    b.specStmts.put(placed, new BodyTranslator.SpecStmt(name, args,
                            arg != null ? b.lambdas.get(arg) : null, List.of(), b.pos));
                } else if (name.equals("everything") && !isVoid) {
                    // modifies(everything()): the wildcard frame.
                    AstNode<StmtExpr> r = b.push(new StmtExpr.All());
                    b.specStmts.put(r, new BodyTranslator.SpecStmt("everything", List.of(), null, List.of(), b.pos));
                } else if (name.equals("old") && argc == 1) {
                    // The pre-state value of the argument. No label: the
                    // procedure's pre-state, which is what old(e) means.
                    // Preconditions and throwsOn guards refuse it at
                    // contract extraction (BodyTranslator.refuseOld).
                    AstNode<StmtExpr> arg = args.get(0);
                    AstNode<StmtExpr> r = b.push(new StmtExpr.Old(arg, Optional.empty()));
                    b.noteDesc(r, b.descOf.get(arg));
                    if (b.bools.contains(arg)) {
                        b.bools.add(r);
                    }
                    if (b.typeVarValues.contains(arg)) {
                        b.typeVarValues.add(r);
                    }
                    TypeRef t = b.staticTypeOf.get(arg);
                    if (t != null) {
                        b.staticTypeOf.put(r, t);
                    }
                } else {
                    throw new JavaViolationException("Unsupported JVerify method: " + name);
                }
            }
        }
    }

    /** forall/exists: translate the lambda impl body with typed binders. */
    private StmtExpr translateQuantifier(String name, BodyTranslator.LambdaHandle lambda) {
        var impl = findImplMethod(lambda);
        if (impl == null) {
            throw new JavaViolationException(name + " requires a single lambda argument");
        }
        var imt = impl.methodTypeSymbol();
        boolean iStatic = impl.flags().has(java.lang.reflect.AccessFlag.STATIC);
        // Binder names from the impl's LVT; types from its descriptor.
        var binderNames = new ArrayList<String>();
        var binderTypes = new ArrayList<HighType>();
        var ish = implShape(impl);
        if (ish == null) {
            throw new JavaViolationException("Unsupported lambda body");
        }
        var iloc = ish.locals();
        // Contract code: as in lambdaBinders, a captured `this` is the
        // receiver of an instance impl, not one of its parameters.
        int capturedParams = lambda.captureCount() - (ctx.contractMode && !iStatic ? 1 : 0);
        int slot = iStatic ? 0 : 1;
        for (int i = 0; i < imt.parameterCount(); i++) {
            if (i >= capturedParams) {
                Locals.Scope ps = iloc.at(slot, 0);
                String pn = ps != null ? ps.name() : ish.slotName().getOrDefault(slot, "p" + i);
                LaurelFactory.refuseReservedName(pn);
                binderNames.add(pn);
                String binderDesc = imt.parameterType(i).descriptorString();
                if (binderDesc.startsWith("[") && ctx.composites.arrays.enabled()) {
                    // A fact over every array value would constrain arrays not yet allocated.
                    throw new JavaViolationException(ArrayModel.QUANTIFIER);
                }
                binderTypes.add(TypeRef.ofDescriptor(binderDesc)
                        .toLaurel(ctx.typeParams, ctx.resolver, ctx.composites));
            }
            String dd = imt.parameterType(i).descriptorString();
            slot += (dd.equals("J") || dd.equals("D")) ? 2 : 1;
        }
        StmtExpr qBody = ArrayAccessTranslator.stripLoadFacts(translateLambdaBody(lambda, Map.of())).val();
        for (int i = binderNames.size() - 1; i >= 0; i--) {
            var param = new org.strata.jverify.laurel.Parameter(
                    LaurelFactory.ident(binderNames.get(i)), LaurelFactory.node(binderTypes.get(i)));
            var mode = name.equals("forall")
                    ? (org.strata.jverify.laurel.QuantifierMode) new org.strata.jverify.laurel.QuantifierMode.Forall()
                    : new org.strata.jverify.laurel.QuantifierMode.Exists();
            qBody = new StmtExpr.Quantifier(mode, param, Optional.empty(), b.e(qBody));
        }
        return qBody;
    }

    /**
     * A supplier-style clause given as a reference to a {@code @Transparent} static method of
     * this program ({@code precondition(Foo::truth)}) is a call to that method with no
     * arguments. Null for anything else -- an instance or library method, an impure
     * one, one taking arguments -- which keeps the refusal.
     */
    private AstNode<StmtExpr> methodReferenceCall(BodyTranslator.LambdaHandle lambda,
                                                  Map<String, String> extraRenames) {
        if (lambda.captureCount() != 0 || lambda.implOwner() == null) {
            return null;
        }
        String fqn = JavaNames.dottedFqn(lambda.implOwner());
        var cm = ctx.resolver.programClassModels.get(fqn);
        if (cm == null) {
            return null;
        }
        java.lang.classfile.MethodModel target = null;
        for (var mm : cm.methods()) {
            if (mm.methodName().stringValue().equals(lambda.implName())
                    && mm.methodTypeSymbol().descriptorString().equals(lambda.implDesc())) {
                target = mm;
            }
        }
        if (target == null || !Declarations.hasAnnotation(target, "Transparent")) {
            return null;
        }
        int arity = target.methodTypeSymbol().parameterCount();
        boolean resultBinder = extraRenames.containsValue(BytecodeToLaurelCompiler.LAUREL_RESULT_BINDING);
        String name = lambda.implName() + ctx.resolver.overloadSuffix(fqn, target);
        AstNode<StmtExpr> call;
        if (lambda.implStatic()) {
            List<AstNode<StmtExpr>> args;
            if (arity == 0 && !resultBinder) {
                args = List.of();
            } else if (arity == 1 && resultBinder) {
                args = List.of(b.e(LaurelFactory.reservedVar_(BytecodeToLaurelCompiler.LAUREL_RESULT_BINDING)));
            } else {
                return null;
            }
            call = b.e(new StmtExpr.StaticCall(LaurelFactory.ident(fqn + "_" + name), args, List.of()));
        } else {
            if (arity != 0 || !resultBinder) {
                return null;
            }
            call = b.e(new StmtExpr.InstanceCall(
                    b.e(LaurelFactory.reservedVar_(BytecodeToLaurelCompiler.LAUREL_RESULT_BINDING)),
                    LaurelFactory.ident(name), List.of()));
        }
        if (ctx.method.info != null) {
            ctx.method.info.callees.add(fqn + "#" + name);
        }
        b.calleeKeys.put(call.val(), Declarations.purityKey(fqn, target));
        return call;
    }

    /** The spec lambda's real binders, or null when its impl method or body is unknown. */
    List<BodyTranslator.LambdaBinder> lambdaBinders(BodyTranslator.LambdaHandle lambda) {
        var impl = findImplMethod(lambda);
        if (impl == null) {
            return null;
        }
        var imt = impl.methodTypeSymbol();
        boolean iStatic = impl.flags().has(java.lang.reflect.AccessFlag.STATIC);
        var ish = implShape(impl);
        if (ish == null) {
            return null;
        }
        var iloc = ish.locals();
        var out = new ArrayList<BodyTranslator.LambdaBinder>();
        int slot = iStatic ? 0 : 1;
        // An instance impl's captured `this` is its RECEIVER, not one of its declared
        // parameters, so it must not be counted against them. Counting it skipped the
        // lambda's real first binder: `(int i) -> i == value` reads a field, so its impl
        // is an instance method capturing `this`, and `i` was never renamed to $result
        // (`Resolution failed: 'i' is not defined`, NestedContractsTest).
        int capturedParams = lambda.captureCount() - (iStatic ? 0 : 1);
        for (int i = 0; i < imt.parameterCount(); i++) {
            String dd = imt.parameterType(i).descriptorString();
            if (i >= capturedParams) {
                var ps = iloc.at(slot, 0);
                out.add(new BodyTranslator.LambdaBinder(
                        ps != null ? ps.name() : ish.slotName().getOrDefault(slot, "p" + i), dd));
            }
            slot += (dd.equals("J") || dd.equals("D")) ? 2 : 1;
        }
        return out;
    }

    java.lang.classfile.MethodModel findImplMethod(BodyTranslator.LambdaHandle lambda) {
        if (b.cm == null || lambda == null) {
            return null;
        }
        for (var mm : b.cm.methods()) {
            if (mm.methodName().stringValue().equals(lambda.implName())
                    && mm.methodTypeSymbol().descriptorString().equals(lambda.implDesc())) {
                return mm;
            }
        }
        return null;
    }

    BodyTranslator.ImplShape implShape(java.lang.classfile.MethodModel impl) {
        var code = (java.lang.classfile.attribute.CodeAttribute) impl
                .findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        if (code == null) {
            return null;
        }
        var slotName = new HashMap<Integer, String>();
        var slotDesc = new HashMap<Integer, String>();
        var lvs = new ArrayList<java.lang.classfile.instruction.LocalVariable>();
        var lvts = new ArrayList<java.lang.classfile.instruction.LocalVariableType>();
        var seq = new ArrayList<CodeElement>();
        for (CodeElement el : code) {
            switch (el) {
                case java.lang.classfile.instruction.LocalVariable lv -> {
                    slotName.put(lv.slot(), lv.name().stringValue());
                    slotDesc.put(lv.slot(), lv.type().stringValue());
                    lvs.add(lv);
                }
                case java.lang.classfile.instruction.LocalVariableType lvt -> lvts.add(lvt);
                case Instruction i -> seq.add(i);
                case java.lang.classfile.instruction.LabelTarget lt -> seq.add(lt);
                default -> {
                }
            }
        }
        ControlFlowGraph icfg = null;
        try {
            icfg = MethodBodyFactory.buildCfg(b.cm, impl);
        } catch (RuntimeException ignore) {
            // straight-line locals
        }
        var locals = Locals.from(lvs, lvts, icfg != null ? icfg.labelPos : Map.of());
        return new BodyTranslator.ImplShape(locals, slotName, slotDesc, seq, MethodBodyFactory.crtRanges(code), icfg);
    }

    /**
     * Translate a spec lambda's impl body as a single expression (the body
     * must be one expression or a single return), under
     * {@code extraRenames} (postcondition's param -> $result etc.) and the
     * contract context.
     */
    AstNode<StmtExpr> translateLambdaBody(BodyTranslator.LambdaHandle lambda, Map<String, String> extraRenames) {
        if (lambda.isMethodRef()) {
            AstNode<StmtExpr> call = methodReferenceCall(lambda, extraRenames);
            if (call != null) {
                return call;
            }
            // Any other method reference in a spec position refuses with the
            // corpus-pinned JCLambda message (only literal lambdas are supported).
            throw new JavaViolationException("Unsupported expression: JCLambda");
        }
        var impl = findImplMethod(lambda);
        if (impl == null) {
            throw new JavaViolationException("Unsupported lambda body");
        }
        BodyTranslator.ImplShape ish = implShape(impl);
        if (ish == null) {
            throw new JavaViolationException("Unsupported lambda body");
        }
        var savedRenames = ctx.method.renames;
        boolean savedContract = ctx.method.inContract;
        int savedPos = b.pos;
        var merged = new HashMap<>(ctx.method.renames);
        merged.putAll(extraRenames);
        ctx.method.renames = merged;
        ctx.method.inContract = true;
        try {
            BodyTranslator inner = new BodyTranslator(ctx, ish.slotName(), ish.slotDesc(), Set.of());
            inner.cm = b.cm;
            inner.isInstance = !impl.flags().has(java.lang.reflect.AccessFlag.STATIC);
            inner.locals = ish.locals();
            inner.crt = ish.crt();   // the impl method's OWN CRT (its pcs key it)
            var seq = ish.seq();
            inner.seqToBci = MethodBodyFactory.bciTable(seq);
            inner.retDesc = impl.methodTypeSymbol().returnType().descriptorString();
            // Parameters are pre-declared so their loads resolve by name.
            var imt = impl.methodTypeSymbol();
            boolean iStatic = impl.flags().has(java.lang.reflect.AccessFlag.STATIC);
            int slot = iStatic ? 0 : 1;
            for (int i = 0; i < imt.parameterCount(); i++) {
                var ps = inner.locals.at(slot, 0);
                inner.declared.add(ps != null
                        ? Locals.slotVarKey(inner.locals, slot, 0) : ("v" + slot));
                String dd = imt.parameterType(i).descriptorString();
                slot += (dd.equals("J") || dd.equals("D")) ? 2 : 1;
            }
            // Structured when the impl has real control flow (boolean
            // diamonds, ternaries), with rollback to the linear form on a
            // structural bail.
            List<AstNode<StmtExpr>> top = null;
            if (ish.cfg() != null && ish.cfg().blocks.size() > 1) {
                try {
                    top = new ControlFlowStructurer(ish.cfg(), MethodBodyFactory.analyze(ish.cfg()),
                            inner, ctx).structure();
                } catch (StructException e) {
                    inner = new BodyTranslator(ctx, ish.slotName(), ish.slotDesc(), Set.of());
                    inner.cm = b.cm;
                    inner.isInstance = !iStatic;   // as the primary path sets it: slot 0 is `this`
                    inner.locals = ish.locals();
                    inner.crt = ish.crt();
                    inner.seqToBci = MethodBodyFactory.bciTable(seq);
                    inner.retDesc = impl.methodTypeSymbol().returnType().descriptorString();
                    int rslot = iStatic ? 0 : 1;
                    for (int i = 0; i < imt.parameterCount(); i++) {
                        var ps = inner.locals.at(rslot, 0);
                        inner.declared.add(ps != null
                                ? Locals.slotVarKey(inner.locals, rslot, 0) : ("v" + rslot));
                        String dd = imt.parameterType(i).descriptorString();
                        rslot += (dd.equals("J") || dd.equals("D")) ? 2 : 1;
                    }
                }
            }
            if (top == null) {
                inner.run(seq);
                top = inner.stmts;
            }
            // Expression body: exactly one Return with a value.
            if (top.size() == 1
                    && top.get(0).val() instanceof StmtExpr.Return ret
                    && ret.values().size() == 1) {
                AstNode<StmtExpr> v = ret.values().get(0);
                b.checkContractArg(v);
                return v;
            }
            throw new JavaViolationException("Unsupported lambda body");
        } finally {
            ctx.method.renames = savedRenames;
            ctx.method.inContract = savedContract;
            b.pos = savedPos;
        }
    }

    /** Refuse allocation inside a contract, checked over a finished Laurel subtree. */
    void checkContractSubtree(AstNode<StmtExpr> n) {
        if (n.val() instanceof StmtExpr.New) {
            throw new JavaViolationException(
                    "object allocation (including lambdas) inside a contract is not yet supported");
        }
        for (AstNode<StmtExpr> child : BodyTranslator.childrenOf(n.val())) {
            checkContractSubtree(child);
        }
    }
}
