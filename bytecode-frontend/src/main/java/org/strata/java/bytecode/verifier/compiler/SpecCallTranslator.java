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

    /** JVerify static spec/assertion calls (JFE's convertJVerifyCall arms + header markers). */
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
                b.addStmt(new StmtExpr.Assert(b.coerce(args.get(0), "Z"), Optional.empty()),
                        BodyTranslator.Narrow.STRIP_SEMI);
            }
            case "assume" -> {
                if (argc != 1) {
                    throw new JavaViolationException("assume should have a single argument");
                }
                b.addStmt(new StmtExpr.Assume(b.coerce(args.get(0), "Z")), BodyTranslator.Narrow.STRIP_SEMI);
            }
            case "implies" -> {
                if (argc != 2) {
                    throw new JavaViolationException("implies should have two arguments");
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
            case "throwsOn" -> {
                // The base of an exceptional-behavior chain: push a handle
                // that .postcondition/.modifies links extend.
                if (argc != 1) {
                    throw new JavaViolationException("A throwsOn call must have exactly one argument");
                }
                AstNode<StmtExpr> handle = b.push(new StmtExpr.LiteralString("$throwsOn"));
                b.specStmts.put(handle, new BodyTranslator.SpecStmt("throwsOn", List.of(args.get(0)),
                        b.lambdas.get(args.get(0)), new ArrayList<>(), b.pos));
            }
            default -> {
                if (b.HEADER_SPEC_NAMES.contains(name) && isVoid) {
                    // Header spec statement: marker consumed by contract extraction.
                    AstNode<StmtExpr> arg = argc >= 1 ? args.get(0) : null;
                    AstNode<StmtExpr> raw = b.e(new StmtExpr.LiteralString("$spec$" + name));
                    FileRange range = b.stmtRange(b.pos, BodyTranslator.Narrow.STRIP_SEMI,
                            arg != null && b.lambdas.containsKey(arg));
                    AstNode<StmtExpr> placed = ctx.placeStatement(raw, range);
                    b.stmts.add(placed);
                    b.specStmts.put(placed, new BodyTranslator.SpecStmt(name, args,
                            arg != null ? b.lambdas.get(arg) : null, List.of(), b.pos));
                } else if (name.equals("everything") && !isVoid) {
                    // modifies(everything()): the wildcard frame.
                    AstNode<StmtExpr> r = b.push(new StmtExpr.All());
                    b.specStmts.put(r, new BodyTranslator.SpecStmt("everything", List.of(), null, List.of(), b.pos));
                } else if (name.equals("old") || name.equals("result")) {
                    // JFE spec vocabulary without a translation: same
                    // refusal as JFE's unsupported spec methods.
                    throw new JavaViolationException("Unsupported JVerify method: " + name);
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
        int slot = iStatic ? 0 : 1;
        for (int i = 0; i < imt.parameterCount(); i++) {
            if (i >= lambda.captureCount()) {
                Locals.Scope ps = iloc.at(slot, 0);
                String pn = ps != null ? ps.name() : ish.slotName().getOrDefault(slot, "p" + i);
                binderNames.add(pn);
                binderTypes.add(TypeRef.ofDescriptor(imt.parameterType(i).descriptorString())
                        .toLaurel(ctx.typeParams, ctx.resolver, ctx.composites));
            }
            String dd = imt.parameterType(i).descriptorString();
            slot += (dd.equals("J") || dd.equals("D")) ? 2 : 1;
        }
        StmtExpr qBody = translateLambdaBody(lambda, Map.of()).val();
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
        for (int i = 0; i < imt.parameterCount(); i++) {
            String dd = imt.parameterType(i).descriptorString();
            if (i >= lambda.captureCount()) {
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
     * Translate a spec lambda's impl body as a single expression (old
     * convertLambdaBody's expression-or-single-return constraint), under
     * {@code extraRenames} (postcondition's param -> $result etc.) and the
     * contract context.
     */
    AstNode<StmtExpr> translateLambdaBody(BodyTranslator.LambdaHandle lambda, Map<String, String> extraRenames) {
        if (lambda.isMethodRef()) {
            // A method reference in a spec position refuses with JFE's
            // JCLambda message (only literal lambdas are supported).
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
                    && ret.value().isPresent()) {
                AstNode<StmtExpr> v = ret.value().get();
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

    /** JFE's refuseAllocationInContract, applied over a finished Laurel subtree. */
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
