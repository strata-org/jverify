package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.LineNumbers;
import org.strata.java.bytecode.frontend.MethodBodyFactory;
import org.strata.java.bytecode.frontend.MethodBodyFactory.CrtEntry;
import org.strata.java.bytecode.frontend.Span;
import org.strata.jverify.laurel.FileRange;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Where a JVerify contract call may appear, checked over a method's bytecode: the
 * two placement rules of JFE's UnsupportedConstructValidator that survive
 * compilation.
 *
 * <ul>
 * <li>{@code isAbstract()} only in a {@code precondition}. The clause that consumes
 * the value is the next JVerify header call in instruction order -- arguments are
 * evaluated before their call, and header calls do not nest -- so this needs no
 * source structure at all.
 * <li>In a constructor, header calls only immediately after the {@code super}/{@code
 * this} call or at the end of the body. This one is about statement ORDER, which the
 * instructions alone do not give, so it reads the CharacterRangeTable's statement
 * ranges. Without one (no {@code -Xjcov}) it reports nothing: missing a misplaced
 * clause is a lost diagnostic, while guessing statement boundaries could accuse a
 * correct constructor.
 * </ul>
 */
final class ContractPlacementChecker {

    private static final String JVERIFY = "org/strata/jverify/JVerify";

    /** JFE's isHeaderContractCall: the clauses that belong in a contract header. */
    private static final Set<String> HEADER_CALLS = Set.of(
            "precondition", "postcondition", "invariant", "decreases", "reads", "modifies");

    private final Reporter reporter;
    private final SourceMap src;

    ContractPlacementChecker(Reporter reporter, SourceMap src) {
        this.reporter = reporter;
        this.src = src;
    }

    /** One instruction with its pc. */
    private record At(Instruction in, int pc) {
    }

    private static List<At> instructions(CodeAttribute code) {
        List<CodeElement> seq = new ArrayList<>();
        code.forEach(seq::add);
        int[] pcs = MethodBodyFactory.bciTable(seq);
        var out = new ArrayList<At>();
        for (int i = 0; i < seq.size(); i++) {
            if (seq.get(i) instanceof Instruction in) {
                out.add(new At(in, pcs[i]));
            }
        }
        return out;
    }

    private static boolean isJVerifyCall(Instruction in, Set<String> names) {
        return in instanceof InvokeInstruction inv
                && inv.opcode() == Opcode.INVOKESTATIC
                && inv.owner().asInternalName().equals(JVERIFY)
                && names.contains(inv.name().stringValue());
    }

    /**
     * Report every {@code isAbstract()} outside a precondition. Returns whether
     * anything was reported, so the caller can demote the method without a second,
     * vaguer refusal.
     */
    boolean checkIsAbstractPlacement(MethodModel m, boolean quiet) {
        var code = m.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        if (code == null) {
            return false;
        }
        var ins = instructions(code);
        var crt = MethodBodyFactory.crtEntries(code);
        var lines = LineNumbers.of(code);
        boolean reported = false;
        for (int i = 0; i < ins.size(); i++) {
            if (!isJVerifyCall(ins.get(i).in(), Set.of("isAbstract", "isAbstractBoolean"))) {
                continue;
            }
            String clause = null;
            for (int j = i + 1; j < ins.size(); j++) {
                if (isJVerifyCall(ins.get(j).in(), HEADER_CALLS)) {
                    clause = ((InvokeInstruction) ins.get(j).in()).name().stringValue();
                    break;
                }
            }
            if ("precondition".equals(clause)) {
                continue;
            }
            if (!quiet) {
                FileRange at = rangeAt(ins.get(i).pc(), crt, lines);
                reporter.reportError(src.uri,
                        at == SourceMap.UNKNOWN_RANGE ? Reporter.NO_RANGE : src.positionRange(at),
                        "isAbstract() in clauses other than a precondition is not supported");
            }
            reported = true;
        }
        return reported;
    }

    /**
     * Report every header contract call in constructor {@code m} that is neither in
     * the run directly after the {@code super}/{@code this} call nor in the run that
     * ends the body.
     *
     * <p>The constructor's own statements are the top-level CRT statements whose
     * source lies inside its body block. That excludes field initializers, which
     * javac splices in after {@code super()} but whose source is the field
     * declaration -- so a contract written first in the body still counts as
     * "immediately after super" even when field initializers run in between.
     */
    void checkConstructorPlacement(java.lang.classfile.ClassModel cm, MethodModel m) {
        Set<String> chainOwners = new java.util.HashSet<>();
        chainOwners.add(cm.thisClass().asInternalName());
        cm.superclass().ifPresent(sc -> chainOwners.add(sc.asInternalName()));
        var code = m.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
        if (code == null) {
            return;
        }
        var crt = MethodBodyFactory.crtEntries(code);
        CrtEntry body = null;
        for (var e : crt) {
            if (e.is(CrtEntry.BLOCK) && (body == null
                    || (e.endPc() - e.startPc()) > (body.endPc() - body.startPc()))) {
                body = e;
            }
        }
        if (body == null) {
            return;
        }
        var ins = instructions(code);
        var statements = new ArrayList<CrtEntry>();
        for (var e : crt) {
            if (e.is(CrtEntry.STATEMENT) && !e.is(CrtEntry.BLOCK) && within(e.span(), body.span())) {
                statements.add(e);
            }
        }
        // Top level only: drop a statement nested inside another one's pc window.
        var top = new ArrayList<CrtEntry>();
        for (var e : statements) {
            boolean nested = statements.stream().anyMatch(o -> o != e
                    && o.startPc() <= e.startPc() && e.endPc() <= o.endPc()
                    && (o.endPc() - o.startPc()) > (e.endPc() - e.startPc()));
            if (!nested && top.stream().noneMatch(t -> t.startPc() == e.startPc() && t.endPc() == e.endPc())) {
                top.add(e);
            }
        }
        top.sort(java.util.Comparator.comparingInt(CrtEntry::startPc));
        // The explicit super(...)/this(...) call is not a body statement for this rule.
        top.removeIf(e -> containsSuperOrThisCall(ins, e, chainOwners));
        boolean[] contract = new boolean[top.size()];
        for (int k = 0; k < top.size(); k++) {
            var e = top.get(k);
            contract[k] = ins.stream().anyMatch(a -> a.pc() >= e.startPc() && a.pc() <= e.endPc()
                    && isJVerifyCall(a.in(), HEADER_CALLS));
        }
        int prefix = 0;
        while (prefix < top.size() && contract[prefix]) {
            prefix++;
        }
        int suffix = top.size();
        while (suffix > prefix && contract[suffix - 1]) {
            suffix--;
        }
        for (int k = prefix; k < suffix; k++) {
            if (contract[k]) {
                var s = top.get(k).span();
                reporter.reportError(src.uri,
                        src.positionRange(src.rangeOf(s.beginLine(), s.beginCol(), s.endLine(), s.endCol())),
                        "for constructors, calls to JVerify contract methods must come either immediately "
                                + "after the call to 'super' or 'this', or at the end of the body");
            }
        }
    }

    /**
     * The statement is the explicit {@code super(...)}/{@code this(...)} call: an
     * {@code <init>} invocation on the superclass or this class. Keyed on the owner so
     * that {@code this.x = new Foo();} -- also an {@code invokespecial <init>} -- is not
     * mistaken for it.
     */
    /**
     * Report every initializer block of {@code cm}, as JFE's validator does: an
     * instance block {@code { ... }} or a static one {@code static { ... }}.
     *
     * <p>Not visible in the instructions -- javac splices a block's statements into
     * every constructor, or into {@code <clinit>} -- but the CharacterRangeTable keeps
     * the block itself: an entry flagged STATEMENT|BLOCK spanning its braces. A field
     * initializer is a STATEMENT only, and a constructor's own body is a BLOCK that is
     * not a statement, so the initializer block is the outermost STATEMENT|BLOCK
     * entry lying outside the constructor body (a block nested inside it is not
     * reported again). Measured with javac 25 -Xjcov. Each instance block appears
     * once per constructor, so spans are deduplicated. Without a CRT nothing is
     * reported.
     */
    void checkInitializerBlocks(java.lang.classfile.ClassModel cm) {
        var seen = new java.util.HashSet<Span>();
        for (var m : cm.methods()) {
            String n = m.methodName().stringValue();
            if (!n.equals("<init>") && !n.equals("<clinit>")) {
                continue;
            }
            var code = m.findAttribute(java.lang.classfile.Attributes.code()).orElse(null);
            if (code == null) {
                continue;
            }
            var crt = MethodBodyFactory.crtEntries(code);
            Span body = null;
            for (var e : crt) {
                if (e.is(CrtEntry.BLOCK) && !e.is(CrtEntry.STATEMENT)) {
                    body = e.span();
                }
            }
            var candidates = new ArrayList<CrtEntry>();
            for (var e : crt) {
                if (e.is(CrtEntry.BLOCK) && e.is(CrtEntry.STATEMENT)
                        && (body == null || !within(e.span(), body))) {
                    candidates.add(e);
                }
            }
            for (var e : candidates) {
                boolean nested = candidates.stream().anyMatch(o -> o != e && within(e.span(), o.span())
                        && !o.span().equals(e.span()));
                if (nested || !seen.add(e.span())) {
                    continue;
                }
                var s = e.span();
                reporter.reportError(src.uri,
                        src.positionRange(src.rangeOf(s.beginLine(), s.beginCol(), s.endLine(), s.endCol())),
                        "an initializer block is not supported");
            }
        }
    }

    private static boolean containsSuperOrThisCall(List<At> ins, CrtEntry e, Set<String> owners) {
        return ins.stream().anyMatch(a -> a.pc() >= e.startPc() && a.pc() <= e.endPc()
                && a.in() instanceof InvokeInstruction inv
                && inv.opcode() == Opcode.INVOKESPECIAL
                && inv.name().stringValue().equals("<init>")
                && owners.contains(inv.owner().asInternalName()));
    }

    private static boolean within(Span inner, Span outer) {
        return compare(outer.beginLine(), outer.beginCol(), inner.beginLine(), inner.beginCol()) <= 0
                && compare(inner.endLine(), inner.endCol(), outer.endLine(), outer.endCol()) <= 0;
    }

    private static int compare(int l1, int c1, int l2, int c2) {
        return l1 != l2 ? Integer.compare(l1, l2) : Integer.compare(c1, c2);
    }

    /** The narrowest CRT statement covering {@code pc}, else its LineNumberTable line. */
    private FileRange rangeAt(int pc, List<CrtEntry> crt, LineNumbers lines) {
        CrtEntry best = null;
        for (var e : crt) {
            if (e.is(CrtEntry.STATEMENT) && e.startPc() <= pc && pc <= e.endPc()
                    && (best == null || (e.endPc() - e.startPc()) < (best.endPc() - best.startPc()))) {
                best = e;
            }
        }
        if (best != null) {
            var s = best.span();
            return src.rangeOf(s.beginLine(), s.beginCol(), s.endLine(), s.endCol());
        }
        int line = lines == null ? -1 : lines.lineAt(pc);
        return line < 0 ? SourceMap.UNKNOWN_RANGE : src.lineSpanRange(line, line);
    }
}
