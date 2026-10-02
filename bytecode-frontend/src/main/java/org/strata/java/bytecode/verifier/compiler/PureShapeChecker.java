package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.MethodBodyFactory;
import org.strata.java.bytecode.frontend.MethodBodyFactory.CrtEntry;
import org.strata.java.bytecode.frontend.Span;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code @Pure} body SHAPE rules of JFE's PureMethodValidator, checked over a
 * classfile.
 *
 * <p>They are statements about source structure -- which statement is last, whether
 * an {@code if} has an {@code else} -- which the instructions do not record, but the
 * CharacterRangeTable does: every statement is a STATEMENT entry spanning its source,
 * a block is a BLOCK entry, an {@code if} carries a FLOW_CONTROLLER entry for its
 * condition and one FLOW_TARGET entry per branch. So the source statement tree is
 * rebuilt from span containment and the rules are applied to it as JFE applies them.
 * Measured with javac 25 -Xjcov. Without a CRT nothing is reported.
 *
 * <p>Mutation is detected only through {@code iinc}: {@code x *= 2} compiles exactly
 * like {@code x = x * 2}, which is not a mutation error. And {@code ++x} compiles like
 * {@code x += 1}, so a prefix increment is reported as {@code +=} -- the rejection is
 * right, the operator in the message may not be.
 */
final class PureShapeChecker {

    private static final int FLOW_CONTROLLER = 0x0008;
    private static final int FLOW_TARGET = 0x0010;
    private static final String JVERIFY = "org/strata/jverify/JVerify";

    private final Reporter reporter;
    private final SourceMap src;
    private final boolean quiet;

    private List<CrtEntry> crt;
    private final List<Instruction> ins = new ArrayList<>();
    private final List<Integer> pcs = new ArrayList<>();
    private final Set<Long> lvtStarts = new HashSet<>();
    /** pc ranges [target, branch] of backward jumps: the loops. */
    private final List<int[]> loops = new ArrayList<>();
    private boolean reported;
    private boolean contractMethod;

    PureShapeChecker(Reporter reporter, SourceMap src, boolean quiet) {
        this.reporter = reporter;
        this.src = src;
        this.quiet = quiet;
    }

    private enum Kind { IF, RETURN, DECLARATION, EXPRESSION, LEFT_ALONE }

    PureShapeChecker forContract() {
        contractMethod = true;
        return this;
    }

    /** Check {@code @Pure} method {@code m}; returns whether anything was reported. */
    boolean check(MethodModel m) {
        var code = m.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        crt = code == null ? List.of() : MethodBodyFactory.crtEntries(code);
        CrtEntry body = null;
        for (var e : crt) {
            if (e.is(CrtEntry.BLOCK) && !e.is(CrtEntry.STATEMENT)
                    && (body == null || width(e) > width(body))) {
                body = e;
            }
        }
        if (m.methodTypeSymbol().returnType().descriptorString().equals("V") && body != null) {
            error(body.span(), "pure method should have a return type");
        }
        if (code == null || body == null) {
            return reported;
        }
        index(code);
        checkMutation();
        checkBlock(body.span());
        return reported;
    }

    private void index(CodeAttribute code) {
        List<CodeElement> seq = new ArrayList<>();
        code.forEach(seq::add);
        int[] bci = MethodBodyFactory.bciTable(seq);
        Map<Label, Integer> labels = new HashMap<>();
        for (int i = 0; i < seq.size(); i++) {
            if (seq.get(i) instanceof LabelTarget lt) {
                labels.put(lt.label(), bci[i]);
            }
        }
        for (int i = 0; i < seq.size(); i++) {
            if (seq.get(i) instanceof Instruction in) {
                ins.add(in);
                pcs.add(bci[i]);
                if (in instanceof BranchInstruction br) {
                    Integer target = labels.get(br.target());
                    if (target != null && target <= bci[i]) {
                        loops.add(new int[] {target, bci[i]});
                    }
                }
            }
        }
        code.findAttribute(java.lang.classfile.Attributes.localVariableTable()).ifPresent(t -> {
            for (var lv : t.localVariables()) {
                lvtStarts.add(((long) lv.slot() << 32) | lv.startPc());
            }
        });
    }

    // --- JFE's checkPureBlock / checkPureBranch ---

    private void checkBlock(Span block) {
        var statements = new ArrayList<CrtEntry>();
        for (var s : directStatements(block)) {
            if (!containsHeaderCall(s)) {
                statements.add(s);
            }
        }
        if (statements.isEmpty()) {
            error(block, "a pure block must end in a return or if-else statement");
            return;
        }
        for (int i = 0; i < statements.size(); i++) {
            var s = statements.get(i);
            boolean last = i == statements.size() - 1;
            switch (kind(s)) {
                case DECLARATION -> {
                    if (last) {
                        error(s.span(), "a pure block must end in a return or if-else statement");
                    }
                }
                case IF -> {
                    var branches = branches(s);
                    if (branches.isEmpty()) {
                        // An empty `{}` then-block emits no code and no CRT entry, so
                        // its only trace is the `if` itself: an empty pure block,
                        // reported on the `if`'s line, where the `{` sits.
                        error(s.span(), "a pure block must end in a return or if-else statement");
                    } else {
                        checkBranch(branches.get(0));
                    }
                    if (branches.size() > 1) {
                        checkBranch(branches.get(1));
                    } else if (last) {
                        error(s.span(), "a pure block may not end in an if statement without an else block");
                    }
                }
                case EXPRESSION -> error(s.span(), last
                        ? "a pure block must end in a return or if-else statement"
                        : "except for the last statement, a pure block may only contain variable declarations");
                default -> {
                    // A loop, a bare block, try, switch, throw or return: left alone,
                    // exactly as JFE leaves them.
                }
            }
        }
    }

    private void checkBranch(CrtEntry branch) {
        if (branch.is(CrtEntry.BLOCK)) {
            checkBlock(branch.span());
            return;
        }
        switch (kind(branch)) {
            case IF -> {
                var branches = branches(branch);
                if (branches.isEmpty()) {
                    error(branch.span(), "a pure block must end in a return or if-else statement");
                } else {
                    checkBranch(branches.get(0));
                }
                if (branches.size() > 1) {
                    checkBranch(branches.get(1));
                } else {
                    error(branch.span(), "a pure block may not end in an if statement without an else block");
                }
            }
            case RETURN -> { }
            default -> error(branch.span(), "a pure block must end in a return or if-else statement");
        }
    }

    // --- Rebuilding the statement tree from CRT spans ---

    /** The statements directly inside {@code block}: STATEMENT entries not nested in another. */
    private List<CrtEntry> directStatements(Span block) {
        var inside = new ArrayList<CrtEntry>();
        for (var e : crt) {
            if (e.is(CrtEntry.STATEMENT) && within(e.span(), block) && !e.span().equals(block)
                    && inside.stream().noneMatch(o -> o.span().equals(e.span()))) {
                inside.add(e);
            }
        }
        var direct = new ArrayList<CrtEntry>();
        for (var e : inside) {
            if (inside.stream().noneMatch(o -> o != e && within(e.span(), o.span()))) {
                direct.add(e);
            }
        }
        direct.sort((a, b) -> compare(a.span().beginLine(), a.span().beginCol(),
                b.span().beginLine(), b.span().beginCol()));
        return direct;
    }

    /** An {@code if}'s branches, then before else: FLOW_TARGET statements directly inside it. */
    private List<CrtEntry> branches(CrtEntry stmt) {
        var targets = new ArrayList<CrtEntry>();
        for (var e : directStatements(stmt.span())) {
            if ((e.flags() & FLOW_TARGET) != 0) {
                targets.add(e);
            }
        }
        return targets;
    }

    private Kind kind(CrtEntry s) {
        if (loops.stream().anyMatch(l -> l[0] >= s.startPc() && l[1] <= s.endPc())) {
            return Kind.LEFT_ALONE;
        }
        for (int i = 0; i < ins.size(); i++) {
            int pc = pcs.get(i);
            if (pc >= s.startPc() && pc <= s.endPc()
                    && (ins.get(i).opcode() == Opcode.TABLESWITCH || ins.get(i).opcode() == Opcode.LOOKUPSWITCH)) {
                return Kind.LEFT_ALONE;
            }
        }
        boolean hasCondition = crt.stream().anyMatch(e -> (e.flags() & FLOW_CONTROLLER) != 0
                && within(e.span(), s.span()) && !insideNestedStatement(e, s));
        // A condition alone does not make an `if`: a ternary inside a return, a
        // declaration or a call has one too. An `if` either has branch statements, or
        // -- with an empty body, which leaves no CRT entry -- ends in its own
        // conditional jump; a ternary ends in whatever consumes its value.
        int lastIdx0 = lastIndex(s);
        if (hasCondition && (!branches(s).isEmpty()
                || (lastIdx0 >= 0 && ins.get(lastIdx0) instanceof BranchInstruction br
                        && br.opcode() != Opcode.GOTO && br.opcode() != Opcode.GOTO_W))) {
            return Kind.IF;
        }
        if (s.is(CrtEntry.BLOCK)) {
            return Kind.LEFT_ALONE;
        }
        int lastIdx = lastIndex(s);
        if (lastIdx < 0) {
            return Kind.LEFT_ALONE;
        }
        Instruction lastIn = ins.get(lastIdx);
        if (lastIn instanceof ReturnInstruction) {
            return Kind.RETURN;
        }
        if (lastIn.opcode() == Opcode.ATHROW) {
            if (contractMethod && !throwsContractException(lastIdx)) {
                return Kind.EXPRESSION;
            }
            return Kind.LEFT_ALONE;
        }
        if (lastIn instanceof StoreInstruction st) {
            int after = lastIdx + 1 < pcs.size() ? pcs.get(lastIdx + 1) : s.endPc() + 1;
            if (lvtStarts.contains(((long) st.slot() << 32) | after)) {
                return Kind.DECLARATION;
            }
        }
        return Kind.EXPRESSION;
    }

    /**
     * The index of the statement's last meaningful instruction: its final one, looking
     * past a trailing {@code pop}/{@code pop2}. javac discards an unused call result
     * that way, so for {@code throwsOn(x < 0);} the call is second to last.
     */
    private int lastIndex(CrtEntry s) {
        int last = -1;
        for (int i = 0; i < ins.size(); i++) {
            if (pcs.get(i) >= s.startPc() && pcs.get(i) <= s.endPc()) {
                last = i;
            }
        }
        while (last >= 0 && pcs.get(last) >= s.startPc()
                && (ins.get(last).opcode() == Opcode.POP || ins.get(last).opcode() == Opcode.POP2)) {
            last--;
        }
        return last >= 0 && pcs.get(last) >= s.startPc() ? last : -1;
    }

    private boolean throwsContractException(int athrowIdx) {
        for (int i = athrowIdx - 1; i >= 0; i--) {
            if (ins.get(i) instanceof InvokeInstruction inv && inv.name().stringValue().equals("<init>")) {
                return inv.owner().asInternalName().equals("org/strata/jverify/ContractException");
            }
        }
        return false;
    }

    private boolean insideNestedStatement(CrtEntry e, CrtEntry s) {
        return directStatements(s.span()).stream().anyMatch(c -> within(e.span(), c.span()));
    }

    /**
     * The statement IS a JVerify call -- JFE's isContractHeaderCall, which skips any
     * expression statement whose call is a JVerify method: the clauses, but also
     * {@code check}, {@code assume} and a {@code throwsOn(...)} chain. The statement's
     * outermost call is its last instruction, so that is the one inspected.
     */
    private boolean containsHeaderCall(CrtEntry s) {
        int idx = lastIndex(s);
        Instruction last = idx < 0 ? null : ins.get(idx);
        return last instanceof InvokeInstruction inv
                && (inv.owner().asInternalName().startsWith(JVERIFY)
                        || inv.owner().asInternalName().equals("org/strata/jverify/Verify"));
    }

    // --- Mutation (JFE's MutationScanner, through iinc) ---

    private void checkMutation() {
        for (int i = 0; i < ins.size(); i++) {
            if (!(ins.get(i) instanceof IncrementInstruction inc) || inLoop(pcs.get(i))) {
                continue;
            }
            // `iinc` then a reload of the slot is the value-AFTER form: `x += c`, or a
            // prefix `++x`, which compile identically -- reported as the compound form.
            // Otherwise a step of one is an increment (`x++`, or the statement `x += 1`).
            boolean reloaded = i + 1 < ins.size()
                    && ins.get(i + 1) instanceof LoadInstruction nx && nx.slot() == inc.slot();
            boolean step = Math.abs(inc.constant()) == 1;
            String op = !reloaded && step
                    ? (inc.constant() > 0 ? "++" : "--")
                    : (inc.constant() > 0 ? "+=" : "-=");
            Span at = narrowestAt(pcs.get(i));
            if (at != null) {
                error(at, "since " + op + " performs mutation, it may not be used in a pure context");
            }
        }
    }

    private boolean inLoop(int pc) {
        return loops.stream().anyMatch(l -> pc >= l[0] && pc <= l[1]);
    }

    private Span narrowestAt(int pc) {
        CrtEntry best = null;
        for (var e : crt) {
            if (e.startPc() <= pc && pc <= e.endPc() && (best == null || width(e) < width(best))) {
                best = e;
            }
        }
        return best == null ? null : best.span();
    }

    // --- Small helpers ---

    private void error(Span s, String message) {
        reported = true;
        if (!quiet) {
            reporter.reportError(src.uri,
                    src.positionRange(src.rangeOf(s.beginLine(), s.beginCol(), s.endLine(), s.endCol())),
                    message);
        }
    }

    private static int width(CrtEntry e) {
        return e.endPc() - e.startPc();
    }

    private static boolean within(Span inner, Span outer) {
        return compare(outer.beginLine(), outer.beginCol(), inner.beginLine(), inner.beginCol()) <= 0
                && compare(inner.endLine(), inner.endCol(), outer.endLine(), outer.endCol()) <= 0;
    }

    private static int compare(int l1, int c1, int l2, int c2) {
        return l1 != l2 ? Integer.compare(l1, l2) : Integer.compare(c1, c2);
    }
}
