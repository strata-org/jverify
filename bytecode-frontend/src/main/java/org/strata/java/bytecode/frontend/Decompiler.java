package org.strata.java.bytecode.frontend;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.RecordPatternExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.UnknownType;

import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Decompiler (JavaParser edition) -- reconstruct Java source from a .class by building a
 * JavaParser AST, with each recovered statement carrying a {@link Span} (line:col) from the
 * CharacterRangeTable ({@code javac -Xjcov}). BYTECODE-ONLY.
 *
 * Supported: constants, locals, arithmetic/bitwise, method calls, object creation, fields, casts,
 * instanceof, numeric conversions, throw, arrays, and the boolean-materialization peephole
 * (comparisons/`&&` feeding a value, e.g. {@code check(a == b)}). Not yet: general control flow
 * (if/loops/switch), invokedynamic (lambdas). Constructors skipped.
 *
 *   Decompiler <Foo.class>            # reconstruct one class
 *   Decompiler <dir> [--emit <out>]   # sweep coverage report (+ optionally write .java files)
 */
public class Decompiler {

    public record Span(int beginLine, int beginCol, int endLine, int endCol) {
        public static final Span NONE = new Span(0, 0, 0, 0);
        public boolean isNone() { return equals(NONE); }
        @Override public String toString() { return beginLine + ":" + beginCol + "-" + endLine + ":" + endCol; }
    }

    public static final DataKey<Span> SPAN = new DataKey<>() { };  // [bytecode] public: read by the Laurel generator
    /**
     * [bytecode] Marks a type declaration hoisted from an ANONYMOUS class
     * (no InnerClasses source name). Public: the Laurel generator defers
     * anonymous-class methods silently, exactly as JFE does.
     */
    public static final DataKey<Boolean> ANONYMOUS = new DataKey<>() { };
    /**
     * Marks a comparison whose operands are floating-point, and which therefore is NOT totally ordered:
     * with a NaN operand every one of {@code <}, {@code <=}, {@code >}, {@code >=} is false, so negating
     * such a test by flipping its operator changes the answer.
     */
    static final DataKey<Boolean> UNORDERED = new DataKey<>() { };

    /**
     * One {@code CharacterRangeTable} entry with the {@code statement} flag, resolved to its bytecode
     * pc window plus source {@link Span}. Statement ranges nest (a statement inside a block inside a
     * method), so a lookup picks the NARROWEST range covering a pc -- the innermost source statement.
     */
    record CrtRange(int startPc, int endPc, Span span) {
        int pcWidth() { return endPc - startPc; }
        /** Source extent, used to break ties between ranges with an identical pc window. */
        int srcWidth() { return (span.endLine() - span.beginLine()) * 1000 + (span.endCol() - span.beginCol()); }
        boolean covers(int pc) { return pc >= startPc && pc <= endPc; }
    }

    /**
     * The innermost source statement span covering the instruction at {@code seqIdx}, or {@link Span#NONE}
     * when the class carries no CRT. Narrowest pc window wins; ties break on the narrower source extent.
     */
    static Span spanOf(List<CrtRange> crt, int[] seqToBci, int seqIdx) {
        if (crt.isEmpty() || seqToBci == null || seqIdx < 0 || seqIdx >= seqToBci.length) return Span.NONE;
        int bci = seqToBci[seqIdx];
        CrtRange best = null;
        for (CrtRange r : crt) {
            if (!r.covers(bci)) continue;
            if (best == null || r.pcWidth() < best.pcWidth()
                    || (r.pcWidth() == best.pcWidth() && r.srcWidth() < best.srcWidth())) best = r;
        }
        return best == null ? Span.NONE : best.span();
    }

    /** Record the source span of the instruction at {@code seqIdx} on a statement (no-op if unknown). */
    static void attachSpan(Statement s, List<CrtRange> crt, int[] seqToBci, int seqIdx) {
        Span sp = spanOf(crt, seqToBci, seqIdx);
        if (!sp.isNone()) s.setData(SPAN, sp);
    }

    /**
     * Collects the imports a recovered class needs. Every type reference in a classfile is fully
     * qualified, but is printed here as a simple name, so the package has to be put back as an import or
     * the output will not compile. Each shortening site reports the pair (internal name, printed form);
     * from those this derives the minimal import that makes the printed form resolve.
     */
    static final class Imports {
        String ownPackage = "";
        String ownTopLevel = "";                             // FQN of the class this file declares
        final Map<String,String> chosen = new TreeMap<>();   // printed leading segment -> FQN to import
        final Set<String> ambiguous = new TreeSet<>();       // segments claimed by more than one FQN

        void reset(String ownPackage, String ownTopLevel) {
            this.ownPackage = ownPackage == null ? "" : ownPackage;
            this.ownTopLevel = ownTopLevel == null ? "" : ownTopLevel;
            chosen.clear(); ambiguous.clear();
        }

        /**
         * Record that {@code printed} (as it appears in the output) denotes the class {@code internal}
         * (a slash/dollar internal name). Only the FIRST segment of {@code printed} has to resolve, so
         * {@code Outer.Inner} needs {@code import pkg.Outer} while a bare {@code Inner} needs the deeper
         * {@code import pkg.Outer.Inner}.
         */
        void note(String internal, String printed) {
            if (internal == null || printed == null || printed.isEmpty()) return;
            String head = printed.contains(".") ? printed.substring(0, printed.indexOf('.')) : printed;
            if (!isIdentifier(head)) return;                 // anonymous/local artifacts, e.g. "1I"
            String[] segs = internal.replace('/', '.').replace('$', '.').split("\\.");
            int cut = -1;
            for (int i = segs.length - 1; i >= 0; i--) if (segs[i].equals(head)) { cut = i; break; }
            if (cut < 0) return;
            String imp = String.join(".", Arrays.copyOfRange(segs, 0, cut + 1));
            if (imp.indexOf('.') < 0) return;                // default package: nothing to import
            int lastDot = imp.lastIndexOf('.');
            String enclosing = imp.substring(0, lastDot);
            // java.lang and the class's own package are already in scope; a NESTED type still needs an
            // import even in the same package, which is why this compares the full enclosing prefix.
            if (enclosing.equals("java.lang") || enclosing.equals(ownPackage)) return;
            // A nested class of THIS file is declared in it, so it is already in scope; importing it would
            // be redundant self-reference.
            if (!ownTopLevel.isEmpty() && (imp.equals(ownTopLevel) || imp.startsWith(ownTopLevel + "."))) return;
            String prev = chosen.putIfAbsent(head, imp);
            if (prev != null && !prev.equals(imp)) ambiguous.add(head);
        }

        /**
         * Add the collected imports. A simple name claimed by two different packages cannot be imported
         * twice; the first-seen wins so its references still resolve, and the clash is counted rather than
         * silently dropped (shortening already discarded which occurrence meant which type).
         */
        void applyTo(CompilationUnit cu) {
            for (Map.Entry<String,String> e : chosen.entrySet()) cu.addImport(e.getValue());
            if (sweeping) { importsEmitted += chosen.size(); importCollisions += ambiguous.size(); }
        }

        static boolean isIdentifier(String s) {
            if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) return false;
            for (int i = 1; i < s.length(); i++) if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
            return true;
        }
    }

    static final Imports imports = new Imports();

    /** Note the import needed for a {@link ClassDesc} printed as {@code printed} (arrays are unwrapped). */
    static void noteType(ClassDesc d, String printed) {
        ClassDesc base = d;
        while (base.isArray()) base = base.componentType();
        if (!base.isClassOrInterface()) return;              // primitives and void need no import
        String desc = base.descriptorString();
        imports.note(desc.substring(1, desc.length() - 1), printed.replace("[]", ""));
    }

    /** {@link #simple} for a REFERENCED type: shortens the name and records the import it now needs. */
    static String simpleRef(String internal) {
        String s = simple(internal);
        imports.note(internal, s);
        return s;
    }

    /** The statement-flagged CRT ranges of a method (empty when the class was compiled without -Xjcov). */
    static List<CrtRange> crtRanges(CodeAttribute code) {
        List<CrtRange> out = new ArrayList<>();
        code.findAttribute(Attributes.characterRangeTable()).ifPresent(crt -> {
            for (CharacterRangeInfo e : crt.characterRangeTable())
                if ((e.flags() & 0x0001) != 0)                      // CRT_STATEMENT
                    out.add(new CrtRange(e.startPc(), e.endPc(), toSpan(e)));
        });
        return out;
    }

    /**
     * End pc of the CRT {@code block} range covering a catch clause whose handler begins at {@code handlerPc},
     * or -1 when there is no CRT.
     *
     * <p>This is how a catch clause's extent is recovered. The exception table records where a handler BEGINS
     * but never where it ends, so the handler and the code following the whole try statement fall into one
     * basic block. javac does record the clause's source extent as a block-flagged CRT range.
     *
     * <p>The range starts just AFTER the handler entry, not at it: the handler's first instruction is the
     * {@code astore} binding the exception, and the clause's source block opens at the statement after it.
     * Hence the small forward window rather than an exact match.
     */
    static int crtBlockEnd(CodeAttribute code, int handlerPc) {
        var attr = code.findAttribute(Attributes.characterRangeTable()).orElse(null);
        if (attr == null) return -1;
        int bestStart = Integer.MAX_VALUE, best = -1;
        for (CharacterRangeInfo e : attr.characterRangeTable()) {
            if ((e.flags() & 0x0002) == 0) continue;                  // CRT_BLOCK
            if (e.startPc() < handlerPc || e.startPc() > handlerPc + 3) continue;
            // closest block to the handler entry; among those the widest, since the clause encloses any
            // nested block that happens to open at the same pc
            if (e.startPc() < bestStart || (e.startPc() == bestStart && e.endPc() > best)) {
                bestStart = e.startPc(); best = e.endPc();
            }
        }
        return best;
    }

    /**
     * Bytecode pc of every element of {@code seq} (instructions + label targets), by accumulating
     * instruction sizes. A label target takes the pc of the instruction that follows it. This is the
     * bridge from the CFG's seq-indices to the pcs the CRT is keyed by.
     */
    static int[] bciTable(List<CodeElement> seq) {
        int[] bci = new int[seq.size()];
        int pc = 0;
        for (int i = 0; i < seq.size(); i++) {
            bci[i] = pc;
            if (seq.get(i) instanceof Instruction in) pc += in.sizeInBytes();
        }
        return bci;
    }

    static boolean sweeping = false;
    static final Map<String,Integer> unsupportedHist = new TreeMap<>();
    static int classUnsupported = 0;
    static int structuredMethods = 0;                              // methods emitted via the structurer
    static final Map<String,Integer> structFallbacks = new TreeMap<>(); // structurer bail reasons -> count
    static int spanOrderViolations = 0;      // methods whose emitted order disagrees with source line order
    static int spannedStmts = 0, totalStmts = 0;                   // CRT span coverage over emitted statements
    static int importsEmitted = 0, importCollisions = 0;           // imports added / clashing simple names

    // --- diagnostics (gated by env DIAG=1): example class#method per failure category ---
    static final boolean DIAG = "1".equals(System.getenv("DIAG"));
    static String curClass = "?", curMethod = "?";
    static final Map<String,List<String>> examples = new TreeMap<>();
    static void noteExample(String category) {
        if (!DIAG) return;
        List<String> l = examples.computeIfAbsent(category, k -> new ArrayList<>());
        if (l.size() < 50) l.add(curClass + "#" + curMethod);
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("--cfg")) { printCfg(Path.of(args[1]), args[2]); return; }
        if (args[0].equals("--struct")) { printStruct(Path.of(args[1]), args[2]); return; }
        if (args[0].equals("--parse")) { parseCheck(Path.of(args[1])); return; }
        Path path = Path.of(args[0]);
        Path emit = null;
        for (int i = 1; i + 1 < args.length; i++) if (args[i].equals("--emit")) emit = Path.of(args[i + 1]);
        if (Files.isDirectory(path)) { sweeping = true; sweep(path, emit); return; }
        // Nested classes live in their own classfiles next to this one, and are needed to rebuild them as
        // members rather than losing them, so the sibling directory is read as the pool.
        System.out.print(render(build(ClassFile.of().parse(path), siblingPool(path))));
    }

    /** The classfiles sitting beside {@code classFile}, indexed by internal name (its nested classes). */
    static Map<String,ClassModel> siblingPool(Path classFile) {
        Map<String,ClassModel> pool = new LinkedHashMap<>();
        Path dir = classFile.toAbsolutePath().getParent();
        if (dir == null) return pool;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.toString().endsWith(".class")).sorted().forEach(p -> {
                try {
                    ClassModel cm = ClassFile.of().parse(p);
                    pool.put(cm.thisClass().asInternalName(), cm);
                } catch (Exception ignore) { }
            });
        } catch (Exception ignore) { }
        return pool;
    }

    /**
     * Report which recovered {@code .java} files are not well-formed Java, by re-parsing them.
     *
     * <p>Separate from the javac recompile check on purpose: javac cannot answer "does this parse?" in
     * isolation. It abandons attribution as soon as any file has a syntax error (so semantic failures are
     * hidden), and {@code -proc:only} still runs the ENTER phase, which reports unresolved imports and
     * duplicate classes -- neither of which is a parse problem. Re-parsing isolates syntactic validity,
     * which is what the emitter alone is responsible for.
     *
     * <p>Prints one line per bad file plus a summary; exits 0 so it composes in scripts.
     */
    static void parseCheck(Path dir) throws Exception {
        // The corpus is modern Java (records, switch expressions and patterns, multi-label cases), and
        // JavaParser defaults to an older level that rejects them outright -- which would be reported as
        // syntax errors in code that is perfectly valid.
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> w = Files.walk(dir)) {
            w.filter(p -> p.toString().endsWith(".java")).sorted().forEach(files::add);
        }
        int bad = 0;
        for (Path p : files) {
            try {
                StaticJavaParser.parse(p);
            } catch (Exception e) {
                bad++;
                String msg = e.getMessage() == null ? e.getClass().getSimpleName()
                        : e.getMessage().replace('\n', ' ').trim();
                System.out.println("PARSE-FAIL " + p + " :: " + (msg.length() > 160 ? msg.substring(0, 160) : msg));
            }
        }
        System.out.printf("parse-check: %d/%d files parse (%.2f%%), %d failed%n",
                files.size() - bad, files.size(),
                files.isEmpty() ? 0.0 : 100.0 * (files.size() - bad) / files.size(), bad);
    }

    // ---- increment A/B: basic-block CFG (pure JDK Class-File API) ----

    /** A basic block: a maximal run of instructions with a single entry and single exit. */
    static final class Blk {
        final int id;
        final List<Integer> instrs = new ArrayList<>();  // seq-indices of the instructions in this block
        Instruction last;                                // last (terminator) instruction of the block
        int fall = -1;      // fallthrough successor block id (conditional-branch not-taken / normal), or -1
        int branch = -1;    // conditional-branch taken target block id, or -1
        int gotoTarget = -1;// unconditional GOTO target block id, or -1
        int defaultCase = -1;               // switch default target block id, or -1
        final List<int[]> cases = new ArrayList<>(); // switch {caseValue, blockId}
        final List<Integer> succ = new ArrayList<>();  // all successor block ids (dedup, for graph algorithms)
        final List<Integer> preds = new ArrayList<>();
        Blk(int id) { this.id = id; }
        boolean isConditional() { return branch >= 0 && fall >= 0; }
        boolean isSwitch()      { return defaultCase >= 0 || !cases.isEmpty(); }
        boolean isReturnOrThrow() { return succ.isEmpty(); }
    }

    /** One exception-table entry, resolved to blocks. type == null means catch-all (finally / any). */
    record Exc(int startBlk, int endBlkId, int handlerBlk, ClassDesc type) { }

    /** Control-flow graph over basic blocks for one method. */
    static final class Cfg {
        final String owner, method;
        final List<CodeElement> seq;      // instructions + label targets, in bytecode order
        final List<Integer> instrIdx;     // seq-indices that are Instructions
        final int[] nextInstr;            // for each seq position, the next instruction seq-index (or -1)
        final Map<Label,Integer> labelPos;// label -> seq-index of its LabelTarget
        final List<Blk> blocks;
        final Map<Integer,Integer> blockOf; // instruction seq-index -> block id
        final List<Exc> excs;             // exception-table entries resolved to blocks (in table order)
        /**
         * Handler block -> the block where its catch clause ENDS, from the CRT. The exception table records
         * only where a handler begins, so without this the clause's extent has to be guessed from liveness.
         */
        final Map<Integer,Integer> catchEnd = new HashMap<>();
        Cfg(String owner, String method, List<CodeElement> seq, List<Integer> instrIdx, int[] nextInstr,
            Map<Label,Integer> labelPos, List<Blk> blocks, Map<Integer,Integer> blockOf, List<Exc> excs) {
            this.owner = owner; this.method = method; this.seq = seq; this.instrIdx = instrIdx;
            this.nextInstr = nextInstr; this.labelPos = labelPos; this.blocks = blocks; this.blockOf = blockOf;
            this.excs = excs;
        }
    }

    static Cfg buildCfg(ClassModel cm, MethodModel m) {
        CodeAttribute code = (CodeAttribute) m.findAttribute(Attributes.code()).orElseThrow();
        List<CodeElement> seq = new ArrayList<>();
        for (CodeElement e : code) if (e instanceof Instruction || e instanceof LabelTarget) seq.add(e);
        int n = seq.size();
        Map<Label,Integer> labelPos = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) if (seq.get(i) instanceof LabelTarget lt) labelPos.put(lt.label(), i);
        int[] nextInstr = new int[n + 1]; nextInstr[n] = -1;
        for (int i = n - 1; i >= 0; i--) nextInstr[i] = (seq.get(i) instanceof Instruction) ? i : nextInstr[i + 1];
        List<Integer> instrIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) if (seq.get(i) instanceof Instruction) instrIdx.add(i);

        List<ExceptionCatch> handlers = code.exceptionHandlers();

        // leaders: first instr; every branch/switch target; the instr after any terminator;
        // and every exception-table boundary (try start, try end, handler entry).
        Set<Integer> leaders = new java.util.TreeSet<>();
        if (!instrIdx.isEmpty()) leaders.add(instrIdx.get(0));
        for (int k = 0; k < instrIdx.size(); k++) {
            Instruction in = (Instruction) seq.get(instrIdx.get(k));
            if (isTerminator(in)) {
                for (int t : branchTargets(in, labelPos, nextInstr)) if (t >= 0) leaders.add(t);
                if (k + 1 < instrIdx.size()) leaders.add(instrIdx.get(k + 1));
            }
        }
        for (ExceptionCatch h : handlers) {
            int s = tgt(h.tryStart(), labelPos, nextInstr);   if (s >= 0) leaders.add(s);
            int e = tgt(h.tryEnd(),   labelPos, nextInstr);   if (e >= 0) leaders.add(e);
            int hd = tgt(h.handler(), labelPos, nextInstr);   if (hd >= 0) leaders.add(hd);
        }
        // The END of a catch block is recorded nowhere in the exception table, so the handler and the code
        // following the whole try statement fall into one basic block -- and that code then gets emitted
        // inside the catch, out of scope for anything after it.
        //
        // The CRT pins the boundary exactly: javac records the clause's source extent as a block-flagged
        // range whose start pc is the handler entry. Where there is no CRT (no -Xjcov) this falls back to the
        // catch PARAMETER's liveness scope, which usually coincides with the body but not always -- the
        // parameter can die at its last read while the body continues.
        // Only for a TYPED catch. A catch-all handler is the finally desugaring, whose folding assumes the
        // handler is a single block (`astore v; <finally>; aload v; athrow`); splitting it there breaks the
        // extraction of the finally body and the length of its inlined normal-path copy.
        int[] bci = bciTable(seq);
        for (ExceptionCatch h : handlers) {
            if (h.catchType().isEmpty()) continue;
            int hd = tgt(h.handler(), labelPos, nextInstr);
            if (hd < 0) continue;
            int bodyEndPc = crtBlockEnd(code, bci[hd]);
            if (bodyEndPc >= 0) {
                for (int i = 0; i < seq.size(); i++)          // first instruction past the clause
                    if (seq.get(i) instanceof Instruction && bci[i] > bodyEndPc) { leaders.add(i); break; }
                continue;
            }
            for (CodeElement ce : code) {
                if (!(ce instanceof LocalVariable lv)) continue;
                Integer st = labelPos.get(lv.startScope()), en = labelPos.get(lv.endScope());
                if (st == null || en == null) continue;
                if (nextInstr[st] == hd || (st >= hd && st <= hd + 2)) {   // the scope opened by this handler
                    int endInstr = nextInstr[en];
                    if (endInstr >= 0) leaders.add(endInstr);
                }
            }
        }

        // group consecutive instructions into blocks (new block at each leader)
        List<Blk> blocks = new ArrayList<>();
        Map<Integer,Integer> blockOf = new HashMap<>();
        Blk cur = null;
        for (int idx : instrIdx) {
            if (cur == null || leaders.contains(idx)) { cur = new Blk(blocks.size()); blocks.add(cur); }
            cur.instrs.add(idx);
            blockOf.put(idx, cur.id);
        }

        // compute successors per block
        for (Blk b : blocks) {
            int lastSeq = b.instrs.get(b.instrs.size() - 1);
            Instruction lastIn = (Instruction) seq.get(lastSeq);
            b.last = lastIn;
            int posOfLast = instrIdx.indexOf(lastSeq);
            int fallSeq = (posOfLast + 1 < instrIdx.size()) ? instrIdx.get(posOfLast + 1) : -1;
            Integer fallBlk = fallSeq >= 0 ? blockOf.get(fallSeq) : null;
            if (lastIn instanceof BranchInstruction br) {
                int tb = blockOf.getOrDefault(tgt(br.target(), labelPos, nextInstr), -1);
                if (isConditionalBranch(br.opcode())) { b.branch = tb; if (fallBlk != null) b.fall = fallBlk; }
                else b.gotoTarget = tb;                          // GOTO
            } else if (lastIn instanceof java.lang.classfile.instruction.TableSwitchInstruction sw) {
                b.defaultCase = blockOf.getOrDefault(tgt(sw.defaultTarget(), labelPos, nextInstr), -1);
                for (var c : sw.cases()) b.cases.add(new int[]{ c.caseValue(), blockOf.getOrDefault(tgt(c.target(), labelPos, nextInstr), -1) });
            } else if (lastIn instanceof java.lang.classfile.instruction.LookupSwitchInstruction sw) {
                b.defaultCase = blockOf.getOrDefault(tgt(sw.defaultTarget(), labelPos, nextInstr), -1);
                for (var c : sw.cases()) b.cases.add(new int[]{ c.caseValue(), blockOf.getOrDefault(tgt(c.target(), labelPos, nextInstr), -1) });
            } else if (!(lastIn instanceof ReturnInstruction) && lastIn.opcode() != Opcode.ATHROW) {
                if (fallBlk != null) b.fall = fallBlk;           // ordinary fallthrough
            }
            // assemble dedup successor list (order: branch/goto/cases/default first, then fall)
            java.util.LinkedHashSet<Integer> s = new java.util.LinkedHashSet<>();
            if (b.branch >= 0) s.add(b.branch);
            if (b.gotoTarget >= 0) s.add(b.gotoTarget);
            for (int[] c : b.cases) if (c[1] >= 0) s.add(c[1]);
            if (b.defaultCase >= 0) s.add(b.defaultCase);
            if (b.fall >= 0) s.add(b.fall);
            b.succ.addAll(s);
        }
        for (Blk b : blocks) for (int su : b.succ) blocks.get(su).preds.add(b.id);

        // resolve the exception table to block ids
        List<Exc> excs = new ArrayList<>();
        for (ExceptionCatch h : handlers) {
            Integer sBlk = blockOf.get(tgt(h.tryStart(), labelPos, nextInstr));
            Integer hBlk = blockOf.get(tgt(h.handler(), labelPos, nextInstr));
            int endSeq = tgt(h.tryEnd(), labelPos, nextInstr);
            int endBlkId = (endSeq < 0) ? blocks.size() : blockOf.getOrDefault(endSeq, blocks.size());
            ClassDesc type = h.catchType().map(ct -> ct.asSymbol()).orElse(null);
            if (sBlk != null && hBlk != null) excs.add(new Exc(sBlk, endBlkId, hBlk, type));
        }
        Cfg built = new Cfg(simple(cm.thisClass().asInternalName()), m.methodName().stringValue(),
                seq, instrIdx, nextInstr, labelPos, blocks, blockOf, excs);
        // Resolve each catch clause's CRT extent to the block that follows it, so the structurer can stop the
        // catch region there instead of inferring the boundary.
        for (ExceptionCatch h : handlers) {
            if (h.catchType().isEmpty()) continue;          // finally: see the note on leaders above
            int hd = tgt(h.handler(), labelPos, nextInstr);
            if (hd < 0) continue;
            int endPc = crtBlockEnd(code, bci[hd]);
            if (endPc < 0) continue;
            for (int i = 0; i < seq.size(); i++)
                if (seq.get(i) instanceof Instruction && bci[i] > endPc) {
                    Integer hb = blockOf.get(hd), fb = blockOf.get(i);
                    if (hb != null && fb != null) built.catchEnd.put(hb, fb);
                    break;
                }
        }
        return built;
    }

    public static void printCfg(Path classFile, String method) throws Exception {
        ClassModel cm = ClassFile.of().parse(classFile);
        MethodModel m = cm.methods().stream()
                .filter(x -> x.methodName().stringValue().equals(method)).findFirst().orElseThrow();
        Cfg cfg = buildCfg(cm, m);
        Dom d = analyze(cfg);
        System.out.println("method " + cfg.owner + "." + cfg.method + "   (" + cfg.blocks.size() + " blocks)\n");
        for (Blk b : cfg.blocks) {
            System.out.println("B" + b.id + ":");
            for (int idx : b.instrs) System.out.printf("    %s%n", ((Instruction) cfg.seq.get(idx)).opcode());
            List<String> outs = b.succ.stream().map(x -> "B" + x).toList();
            System.out.println("  -> " + (outs.isEmpty() ? "(exit)" : String.join(", ", outs)));
            System.out.printf("     idom=%s ipdom=%s%s%n%n",
                    b.id == 0 ? "-" : "B" + d.idom[b.id],
                    d.ipdom[b.id] < 0 ? "exit" : "B" + d.ipdom[b.id],
                    d.isLoopHeader(b.id) ? "  LOOP-HEADER body=" + d.loops.get(b.id) + " latch=B" + d.loopTail.get(b.id) : "");
        }
    }

    static boolean isTerminator(Instruction in) {
        return in instanceof BranchInstruction || in instanceof ReturnInstruction
                || in instanceof java.lang.classfile.instruction.TableSwitchInstruction
                || in instanceof java.lang.classfile.instruction.LookupSwitchInstruction
                || in.opcode() == Opcode.ATHROW;
    }

    static List<Integer> branchTargets(Instruction in, Map<Label,Integer> labelPos, int[] nextInstr) {
        List<Integer> t = new ArrayList<>();
        switch (in) {
            case BranchInstruction b -> t.add(tgt(b.target(), labelPos, nextInstr));
            case java.lang.classfile.instruction.TableSwitchInstruction sw -> {
                t.add(tgt(sw.defaultTarget(), labelPos, nextInstr));
                for (var c : sw.cases()) t.add(tgt(c.target(), labelPos, nextInstr));
            }
            case java.lang.classfile.instruction.LookupSwitchInstruction sw -> {
                t.add(tgt(sw.defaultTarget(), labelPos, nextInstr));
                for (var c : sw.cases()) t.add(tgt(c.target(), labelPos, nextInstr));
            }
            default -> { }
        }
        return t;
    }
    static int tgt(Label l, Map<Label,Integer> labelPos, int[] nextInstr) {
        Integer pos = labelPos.get(l);
        return pos == null ? -1 : nextInstr[pos];
    }

    // ---- increment C-1: dominators, post-dominators, natural loops ----

    /** Dominance/loop analysis over a {@link Cfg}. */
    static final class Dom {
        final int n;
        final BitSet[] dom;   // dom[b]  = blocks that dominate b (incl. b)
        final int[] idom;     // immediate dominator (idom[entry] = entry)
        final BitSet[] pdom;  // pdom[b] = blocks that post-dominate b (incl. b); index n = virtual exit
        final int[] ipdom;    // immediate post-dominator (block id, or -1 == exit)
        final Map<Integer,Set<Integer>> loops = new HashMap<>(); // header -> body block ids (incl. header)
        final Map<Integer,Integer> loopTail = new HashMap<>();   // header -> the back-edge source (latch)
        Dom(int n, BitSet[] dom, int[] idom, BitSet[] pdom, int[] ipdom) {
            this.n = n; this.dom = dom; this.idom = idom; this.pdom = pdom; this.ipdom = ipdom;
        }
        boolean dominates(int a, int b) { return dom[b].get(a); }
        boolean isLoopHeader(int b) { return loops.containsKey(b); }
    }

    static Dom analyze(Cfg cfg) {
        int n = cfg.blocks.size();
        // forward dominators (root = entry block 0)
        List<List<Integer>> preds = new ArrayList<>();
        for (Blk b : cfg.blocks) preds.add(b.preds);
        BitSet[] dom = domSets(n, 0, preds);
        int[] idom = idomFrom(dom, 0);

        // post-dominators: dominators on the reverse graph with a virtual exit at index n
        int X = n;
        List<List<Integer>> rpreds = new ArrayList<>();
        for (int i = 0; i <= n; i++) rpreds.add(new ArrayList<>());
        List<Integer> exits = new ArrayList<>();
        for (Blk b : cfg.blocks) {
            if (b.succ.isEmpty()) { rpreds.get(b.id).add(X); exits.add(b.id); }   // reverse edge X -> exit
            else for (int s : b.succ) rpreds.get(b.id).add(s);                    // reverse pred of b is its succ
        }
        rpreds.get(X).addAll(exits);                                             // X's reverse-preds are the exits
        BitSet[] pdom = domSets(n + 1, X, rpreds);
        int[] ipdomFull = idomFrom(pdom, X);
        int[] ipdom = new int[n];
        for (int b = 0; b < n; b++) ipdom[b] = (ipdomFull[b] == X) ? -1 : ipdomFull[b];

        Dom d = new Dom(n, dom, idom, pdom, ipdom);
        // natural loops from back-edges (edge b -> h with h dominating b)
        for (Blk b : cfg.blocks)
            for (int s : b.succ)
                if (d.dominates(s, b.id)) {              // s is a header, b is the latch
                    Set<Integer> body = naturalLoop(s, b.id, cfg.blocks);
                    d.loops.merge(s, body, (a, c) -> { a.addAll(c); return a; });
                    d.loopTail.put(s, b.id);
                }
        return d;
    }

    /** Iterative dominator-set dataflow. preds.get(b) = graph predecessors of b; root has dom = {root}. */
    static BitSet[] domSets(int n, int root, List<List<Integer>> preds) {
        BitSet[] dom = new BitSet[n];
        for (int i = 0; i < n; i++) { dom[i] = new BitSet(n); dom[i].set(0, n); }
        dom[root].clear(); dom[root].set(root);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int b = 0; b < n; b++) {
                if (b == root) continue;
                BitSet nd = new BitSet(n);
                boolean first = true;
                for (int p : preds.get(b)) {
                    if (first) { nd.or(dom[p]); first = false; } else nd.and(dom[p]);
                }
                if (first) nd.clear();   // unreachable node: only itself
                nd.set(b);
                if (!nd.equals(dom[b])) { dom[b] = nd; changed = true; }
            }
        }
        return dom;
    }

    /** Immediate dominator = the strict dominator with the largest dominator set (closest). */
    static int[] idomFrom(BitSet[] dom, int root) {
        int n = dom.length;
        int[] idom = new int[n];
        for (int b = 0; b < n; b++) {
            if (b == root) { idom[b] = root; continue; }
            int best = -1;
            for (int dd = dom[b].nextSetBit(0); dd >= 0; dd = dom[b].nextSetBit(dd + 1)) {
                if (dd == b) continue;
                if (best == -1 || dom[dd].cardinality() > dom[best].cardinality()) best = dd;
            }
            idom[b] = best;
        }
        return idom;
    }

    /** Natural loop of back-edge tail -> head: head plus everything reaching tail without leaving via head. */
    static Set<Integer> naturalLoop(int head, int tail, List<Blk> blocks) {
        Set<Integer> loop = new HashSet<>();
        loop.add(head);
        Deque<Integer> stack = new ArrayDeque<>();
        if (loop.add(tail)) stack.push(tail);
        while (!stack.isEmpty()) {
            int m = stack.pop();
            for (int p : blocks.get(m).preds) if (p != head && loop.add(p)) stack.push(p);
        }
        return loop;
    }

    // ---- increment C-2: recursive pattern-based structurer (sequence / if / while / do-while) ----

    static final class StructException extends RuntimeException {
        StructException(String m) { super(m); }
    }

    static final class Structurer {
        final Cfg cfg;
        final Dom dom;
        final Body body;
        final boolean[] visited;       // blocks whose instructions were consumed by the structurer
        /** enclosing breakable contexts (innermost first): loop or switch.
         *  continueTarget = the block a `continue` jumps to (header for while, latch/condition for do-while). */
        record Ctx(boolean loop, int header, int exit, int latch, int continueTarget) { }
        final Deque<Ctx> ctx = new ArrayDeque<>();
        final Set<Integer> emittingLoops = new HashSet<>();  // loop headers currently being structured
        // [bytecode] loop headers whose region walk may enter them once despite the stop set
        // (the while(true) fallback starts its body at the header; see emitLoop0).
        final Set<Integer> loopEntryPass = new HashSet<>();
        // [bytecode] headers of while(true)-fallback loops: inside them an if-arm edge to the
        // loop exit must synthesize `break` (there is no condition test to fall out through);
        // in condition-tested loops the loop machinery owns the exit and synthesizing would
        // duplicate control flow.
        final Set<Integer> infiniteHeaders = new HashSet<>();
        // try/catch: try-start block -> its exception entries (grouped); guard + per-block leading skip
        final Map<Integer,List<Exc>> triesByStart = new HashMap<>();
        final Set<Integer> emittedTries = new HashSet<>();
        final Set<Integer> emittedSync = new HashSet<>();
        final Set<Integer> declinedTypeSwitch = new HashSet<>();  // typeSwitch loop headers flagged unsupported once
        final Map<Integer,Integer> skipLead = new HashMap<>();   // block -> leading instrs to skip (catch astore)
        final Map<Integer,Integer> skipTail = new HashMap<>();   // block -> trailing instrs to skip (finally rethrow)
        final Map<Integer,String> loopLabel = new HashMap<>();   // loop header -> source label, if a jump needs one
        final Map<Integer,int[]> patternBind = new HashMap<>();  // cond block -> {bindSlot, bindPos} of `instanceof T t`
        int guard = 0;                 // iteration guard against pathological cycles
        int depth = 0;                 // recursion-depth guard (bails cleanly before StackOverflow)

        Structurer(Cfg cfg, Dom dom, Body body) {
            this.cfg = cfg; this.dom = dom; this.body = body; this.visited = new boolean[cfg.blocks.size()];
            for (Exc e : cfg.excs) triesByStart.computeIfAbsent(e.startBlk(), k -> new ArrayList<>()).add(e);
        }

        BlockStmt structure() {
            BlockStmt root = new BlockStmt();
            for (Statement s : region(0, Set.of())) root.addStatement(s);
            // any residual scratch statements (defensive) get appended in order
            for (Statement s : new ArrayList<>(body.stmts)) root.addStatement(s);
            body.stmts.clear();
            // safety: every block reachable from entry must have been consumed, else we dropped code
            Deque<Integer> q = new ArrayDeque<>(); q.add(0);
            boolean[] reach = new boolean[cfg.blocks.size()]; reach[0] = true;
            while (!q.isEmpty()) { int x = q.poll(); for (int s : cfg.blocks.get(x).succ) if (!reach[s]) { reach[s] = true; q.add(s); } }
            for (int i = 0; i < reach.length; i++) if (reach[i] && !visited[i]) throw new StructException("unstructured block B" + i);
            return root;
        }

        /** Emit the statements of the region beginning at {@code entry}, stopping before any block in {@code stop}. */
        List<Statement> region(int entry, Set<Integer> stop) {
            if (++depth > 3000) { depth--; throw new StructException("recursion too deep"); }
            try { return region0(entry, stop); } finally { depth--; }
        }
        List<Statement> region0(int entry, Set<Integer> stop) {
            List<Statement> out = new ArrayList<>();
            int cur = entry;
            while (cur >= 0 && (!stop.contains(cur) || loopEntryPass.remove(cur))) {
                if (++guard > 100000) throw new StructException("structurer guard tripped");
                Blk b = cfg.blocks.get(cur);
                visited[cur] = true;

                // `try { synchronized (x) { ... } } catch (E e)` protects the monitorenter prologue itself, so
                // a typed catch can start at the very block that opens the synchronized statement. Claiming it
                // for the synchronized first dropped the try outright and let the exception escape, so the
                // outer construct goes first and the region walk reaches the monitorenter inside its body.
                boolean tryHere = triesByStart.containsKey(cur) && !emittedTries.contains(cur);
                boolean typedTryHere = tryHere && triesByStart.get(cur).stream().anyMatch(e -> e.type() != null);
                if (!typedTryHere && b.last != null && b.last.opcode() == Opcode.MONITORENTER
                        && !emittedSync.contains(cur)) { cur = emitSync(cur, stop, out); continue; }
                if (tryHere) { cur = emitTry(cur, stop, out); continue; }

                // Pattern switch (SwitchBootstraps.typeSwitch). A guarded switch's restart back-edge makes the
                // switch block a loop header, so intercept it at its preheader BEFORE emitLoop claims it.
                {
                    int psSw = -1, psSetup = cur;
                    if (typeSwitchIndy(b) != null) psSw = cur;                    // b is the switch block
                    else if (!b.isConditional() && !dom.isLoopHeader(cur)) {      // b may be a guarded preheader
                        int nx = b.gotoTarget >= 0 ? b.gotoTarget : b.fall;
                        if (nx >= 0 && typeSwitchIndy(cfg.blocks.get(nx)) != null && dom.isLoopHeader(nx)) psSw = nx;
                    }
                    if (psSw >= 0) {
                        Integer pf = tryPatternSwitch(psSetup, psSw, stop, out);
                        if (pf != null) { cur = pf; continue; }                   // else fall through (graceful)
                        // Declined typeSwitch that is a loop header (e.g. record-pattern deconstruction):
                        // emitLoop's fallback drops the header's instructions, so flag the unsupported
                        // typeSwitch here to keep the coverage report honest (once per switch block).
                        if (dom.isLoopHeader(psSw) && declinedTypeSwitch.add(psSw))
                            markUnsupported(typeSwitchIndy(cfg.blocks.get(psSw)));
                    }
                }

                { Integer af = tryAssert(cur, out); if (af != null) { cur = af; continue; } }

                // treat as a loop header only if we are not already emitting this very loop's body
                if (dom.isLoopHeader(cur) && !emittingLoops.contains(cur)) { cur = emitLoop(cur, stop, out); continue; }

                if (isStringSwitch(b)) { cur = emitStringSwitch(cur, stop, out); continue; }
                Integer smerge = switchValueDiamond(cur);          // value-producing switch (switch expression)
                if (smerge != null) { emitSwitchExpr(cur, smerge, out); cur = smerge; continue; }
                if (b.isSwitch())          { cur = emitSwitch(cur, stop, out); continue; }

                Integer merge = booleanDiamond(cur);
                if (merge != null) { emitDiamondValue(b, out); cur = merge; continue; }

                foldChainPatternBind(cur);

                Integer tmerge = valueDiamond(cur);
                if (tmerge != null) { emitValueDiamond(cur, tmerge, out); cur = tmerge; continue; }

                if (b.isConditional())     { cur = emitIf(cur, stop, out); continue; }

                // ordinary block: straight-line statements, then follow the single edge
                translateBlock(b, out);
                boolean viaGoto = b.gotoTarget >= 0;
                int next = viaGoto ? b.gotoTarget : b.fall;   // -1 for return/throw
                Statement je = loopEdge(b.id, next, viaGoto);  // break/continue if this edge leaves the loop nest
                if (je != null) { out.add(je); return out; }
                cur = next;
            }
            return out;
        }

        /**
         * If the explicit jump {@code from -> next} leaves the nest, return break (to the innermost
         * loop/switch exit) or continue (to the nearest enclosing loop's continue target). Only
         * explicit jumps (goto / branch) are break/continue; natural fall-through is structural.
         */
        Statement loopEdge(int from, int next, boolean viaGoto) {
            if (next < 0 || !viaGoto || ctx.isEmpty()) return null;
            Ctx top = ctx.peek();
            if (next == top.exit()) return new BreakStmt();           // break the innermost loop/switch
            boolean innermostLoop = true;
            for (Ctx c : ctx) {                                       // innermost first
                if (!c.loop()) continue;
                if (next == c.continueTarget() && from != c.latch())
                    return innermostLoop ? new ContinueStmt() : new ContinueStmt(labelFor(c.header()));
                // A jump to an OUTER loop's exit is `break label`. Returning null here instead dropped the
                // jump entirely, which for `break outer` from an inner loop left the inner loop with no way
                // out at all.
                if (!innermostLoop && next == c.exit()) return new BreakStmt(labelFor(c.header()));
                innermostLoop = false;
            }
            return null;
        }

        /**
         * The label naming the loop headed by {@code header}, minted on first use.
         *
         * <p>A label only exists in source when something jumps past the innermost loop, so it is allocated
         * on demand and the loop is wrapped only if a jump actually asked for it.
         */
        SimpleName labelFor(int header) {
            return new SimpleName(loopLabel.computeIfAbsent(header, k -> "L" + loopLabel.size()));
        }

        /** Add a loop statement, wrapped in its label if some jump inside it needed one. */
        void addLoop(List<Statement> out, Statement loop, int header) {
            String lbl = loopLabel.get(header);
            out.add(lbl == null ? loop : new LabeledStmt(new SimpleName(lbl), loop));
        }

        // --- assert ---

        /**
         * Recover {@code assert cond;} / {@code assert cond : msg;} from its desugaring:
         *
         * <pre>
         *   guard: getstatic $assertionsDisabled
         *          ifne AFTER                        // assertions off -> skip
         *   cond:  &lt;condition&gt;
         *          if&lt;holds&gt; AFTER                   // assertion holds -> skip the throw
         *   throw: new AssertionError [msg]; athrow
         *   AFTER: ...
         * </pre>
         *
         * <p>Both branches target the SAME block, which is what identifies the shape. Recovering it is not
         * cosmetic: the throw arm means AFTER does not post-dominate the guard, so the generic if/else path
         * finds no merge point and duplicates the whole remainder of the method into both arms -- once per
         * assert, so the output grows exponentially in the number of assertions.
         *
         * <p>Returns the block to continue at, or null if this is not an assert.
         */
        Integer tryAssert(int guardBlk, List<Statement> out) {
            Blk g = cfg.blocks.get(guardBlk);
            if (!g.isConditional()) return null;
            // The guard is the LAST TWO instructions of its block: nothing forces a block boundary before
            // the getstatic, so any straight-line code preceding the assert shares the block with it.
            List<CodeElement> ins = elems(g);
            int n = ins.size();
            if (n < 2) return null;
            if (!(ins.get(n - 2) instanceof FieldInstruction fi)
                    || fi.opcode() != Opcode.GETSTATIC
                    || !fi.name().stringValue().equals("$assertionsDisabled")) return null;
            if (g.last.opcode() != Opcode.IFNE) return null;

            int after = g.branch, condBlk = g.fall;
            if (after < 0 || condBlk < 0 || condBlk >= cfg.blocks.size()) return null;
            Blk c = cfg.blocks.get(condBlk);
            if (!c.isConditional() || c.branch != after) return null;   // the "assertion holds" edge
            int throwBlk = c.fall;
            if (throwBlk < 0 || throwBlk >= cfg.blocks.size()) return null;
            Blk t = cfg.blocks.get(throwBlk);
            if (t.last == null || t.last.opcode() != Opcode.ATHROW || !throwsAssertionError(t)) return null;

            visited[guardBlk] = true;
            int mark = body.stmts.size();
            body.translateSpan(ins, elemIdxs(g), 0, n - 2);   // code preceding the assert
            drainInto(out, mark);

            // the branch is taken when the assertion HOLDS, and conditionOf yields the fall-through
            // condition, so the asserted expression is its negation
            visited[condBlk] = true;      // consumed by conditionOf below, not by region()
            Expression cond = negate(conditionOf(c, out));
            visited[throwBlk] = true;
            Expression msg = assertMessage(t);
            out.add(atBlk(msg == null ? new AssertStmt(cond) : new AssertStmt(cond, msg), condBlk));
            return after;
        }

        /** True if this block constructs a {@code java.lang.AssertionError} (the throw arm of an assert). */
        boolean throwsAssertionError(Blk t) {
            for (int idx : t.instrs)
                if (cfg.seq.get(idx) instanceof InvokeInstruction inv
                        && inv.name().stringValue().equals("<init>")
                        && inv.owner().asInternalName().equals("java/lang/AssertionError")) return true;
            return false;
        }

        /** The detail message of {@code assert cond : msg}, or null for the message-less form. */
        Expression assertMessage(Blk t) {
            int mark = body.stmts.size();
            List<CodeElement> ins = elems(t);
            body.translateSpan(ins, elemIdxs(t), 0, ins.size() - 1);   // everything but the athrow
            body.stmts.subList(mark, body.stmts.size()).clear();        // discard any stray statements
            Expression e = body.stack.isEmpty() ? null : body.stack.pop();
            return e instanceof ObjectCreationExpr oc && !oc.getArguments().isEmpty()
                    ? oc.getArgument(0) : null;
        }

        // --- if / if-else ---
        /**
         * Recover {@code o instanceof P(int a, int b)}. javac tests the type, casts into a temp, then runs
         * the same per-component chain as a switch record pattern -- except every failing edge jumps to the
         * instanceof's own false edge instead of restarting a typeSwitch, and the then-arm continues inside
         * the block that binds the last component.
         *
         * <p>Folds the chain into {@code cond}'s pattern and returns where the then-arm really begins, or -1
         * to leave the caller's handling alone. Nothing is mutated until every check has passed.
         */
        int instanceofRecordPattern(int condBlk, Expression cond) {
            Blk c = cfg.blocks.get(condBlk);
            if (c.last.opcode() != Opcode.IFEQ) return -1;
            if (!(cond instanceof InstanceOfExpr io) || io.getPattern().isPresent()) return -1;
            int n = c.instrs.size();
            if (n < 2 || !(insAt(c, n - 2) instanceof TypeCheckInstruction ti)
                    || ti.opcode() != Opcode.INSTANCEOF) return -1;
            // the true edge casts the scrutinee into a temp and loads it for the first accessor
            int hb = c.fall;
            if (hb < 0 || hb >= cfg.blocks.size()) return -1;
            Blk h = cfg.blocks.get(hb);
            if (h.instrs.size() != 4 || !(insAt(h, 0) instanceof LoadInstruction)) return -1;
            if (!(insAt(h, 1) instanceof TypeCheckInstruction cc) || cc.opcode() != Opcode.CHECKCAST) return -1;
            if (!(insAt(h, 2) instanceof StoreInstruction rs)) return -1;
            if (!(insAt(h, 3) instanceof LoadInstruction rl) || rl.slot() != rs.slot()) return -1;

            Decon d = walkComponents(hb, rs.slot());
            if (d == null || d.failTarget != c.branch || d.bodyLead == 0) return -1;

            io.setPattern(new RecordPatternExpr(new NodeList<>(), io.getType(), componentPatterns(d.root)));

            visited[hb] = true;
            for (int b : d.blks) visited[b] = true;
            // Each accessor call is protected so a throwing accessor becomes a MatchException. That handler is
            // part of the desugaring, not of the source, so suppress the try and account for its blocks.
            for (Exc e : cfg.excs)
                if (d.blks.contains(e.startBlk())) {
                    emittedTries.add(e.startBlk());
                    markChainVisited(e.handlerBlk(), -1);
                }
            skipLead.put(d.bodyEntry, d.bodyLead);
            return d.bodyEntry;
        }

        int emitIf(int condBlk, Set<Integer> stop, List<Statement> out) {
            Blk c = cfg.blocks.get(condBlk);
            Expression cond = conditionOf(c, out);   // "fall" (not-branch) condition
            int thenE = c.fall, elseE = c.branch;
            int follow = dom.ipdom[condBlk];

            // a record pattern's deconstruction chain sits between the test and the real then-arm
            int recEntry = instanceofRecordPattern(condBlk, cond);
            if (recEntry >= 0) thenE = recEntry;

            Set<Integer> sub = plus(stop, follow);
            if (elseE == follow || elseE < 0 || stop.contains(elseE)) {
                // if-then only (branch goes straight to the merge / out of region).
                // [bytecode] Unless the branch edge leaves the loop nest: a branch to the loop
                // exit that is NOT the merge is a real `break`/`continue` and dropping it
                // detached the loop's only way out (while(true) bodies especially).
                Statement je = null;
                if (elseE >= 0 && inInfiniteLoop() && elseE == ctx.peek().exit()) {
                    // The merge/ipdom can coincide with the while(true) loop's exit
                    // (all paths out go through it): that edge is the loop's break.
                    // Condition-tested loops keep the original structuring -- their
                    // loop machinery owns the exit edge.
                    je = new BreakStmt();
                }
                if (je != null) {
                    out.add(atBlk(new IfStmt(negate(cond), new BlockStmt(nl(List.of(je))), null), condBlk));
                    return thenE;   // structure the fall path inline after the guarded jump
                }
                List<Statement> t = region(thenE, sub);
                out.add(atBlk(new IfStmt(cond, new BlockStmt(nl(t)), null), condBlk));
            } else if (thenE == follow || stop.contains(thenE)) {
                // then is empty -> invert into if(!cond){ else }
                // [bytecode] Same guard for the fall edge: a fall-through arm that resolves to
                // the loop exit is a `break`, not an empty branch.
                Statement je = null;
                if (inInfiniteLoop() && thenE == ctx.peek().exit()) {
                    je = new BreakStmt();
                }
                if (je != null) {
                    out.add(atBlk(new IfStmt(cond, new BlockStmt(nl(List.of(je))), null), condBlk));
                    return elseE;   // structure the branch path inline after the guarded jump
                }
                List<Statement> e = region(elseE, sub);
                out.add(atBlk(new IfStmt(negate(cond), new BlockStmt(nl(e)), null), condBlk));
            } else {
                List<Statement> t = region(thenE, sub);
                List<Statement> e = region(elseE, sub);
                out.add(atBlk(new IfStmt(cond, new BlockStmt(nl(t)), new BlockStmt(nl(e))), condBlk));
            }
            return follow;
        }

        /** [bytecode] True when the innermost breakable context is a while(true)-fallback loop. */
        boolean inInfiniteLoop() {
            return !ctx.isEmpty() && ctx.peek().loop() && infiniteHeaders.contains(ctx.peek().header());
        }

        /**
         * True if {@code b} returns to {@code target} contributing nothing: the target itself, or a chain of
         * blocks holding only a goto. Such an edge means the loop body is empty and whatever work the loop
         * does must live in the header.
         */
        boolean jumpsStraightBackTo(int b, int target) {
            for (int i = 0, cur = b; i < 8 && cur >= 0 && cur < cfg.blocks.size(); i++) {
                if (cur == target) return true;
                Blk k = cfg.blocks.get(cur);
                if (k.instrs.size() != 1 || k.gotoTarget < 0) return false;
                cur = k.gotoTarget;
            }
            return false;
        }

        // --- while (pre-tested) / do-while (post-tested) / while(true) fallback ---
        int emitLoop(int header, Set<Integer> stop, List<Statement> out) {
            if (!emittingLoops.add(header)) throw new StructException("loop re-entry B" + header);
            try { return emitLoop0(header, stop, out); } finally { emittingLoops.remove(header); }
        }
        int emitLoop0(int header, Set<Integer> stop, List<Statement> out) {
            Blk h = cfg.blocks.get(header);
            Set<Integer> bodyBlocks = dom.loops.get(header);
            int latch = dom.loopTail.get(header);
            Blk lb = cfg.blocks.get(latch);

            // do-while: the back-edge source (latch) carries the conditional test at the loop bottom
            if (lb.isConditional() && (lb.branch == header || lb.fall == header)) {
                boolean backOnBranch = (lb.branch == header);
                int exit = backOnBranch ? lb.fall : lb.branch;
                visited[latch] = true;              // latch consumed via conditionOf below, not region
                ctx.push(new Ctx(true, header, exit, latch, latch));   // do-while: continue -> the bottom test
                List<Statement> bodyStmts = region(header, plus(plus(stop, latch), exit));
                Expression cond = conditionOf(lb, bodyStmts);   // latch body -> statements, test -> loop cond
                if (backOnBranch) cond = negate(cond);          // continues while the branch-back condition holds
                ctx.pop();
                addLoop(out, atBlk(new DoStmt(new BlockStmt(nl(bodyStmts)), cond), latch), header);
                return exit;
            }

            // Bottom-tested loop with the test in the HEADER: `while (true) { body; if (c) break; }` puts the
            // body ahead of the test in the header block and leaves its in-loop edge doing nothing but
            // jumping back. Read as a pre-tested while, the header's prefix drains out as the condition's
            // side effects -- hoisting the body clear of the loop and leaving an empty body with a condition
            // that can never change, which is to say an infinite loop.
            if (h.isConditional() && bodyBlocks.contains(h.fall) != bodyBlocks.contains(h.branch)) {
                boolean backOnBranch = bodyBlocks.contains(h.branch);
                int inLoop = backOnBranch ? h.branch : h.fall;
                int exit = backOnBranch ? h.fall : h.branch;
                if (jumpsStraightBackTo(inLoop, header)) {
                    markChainVisited(inLoop, header);
                    List<Statement> bodyStmts = new ArrayList<>();
                    Expression cond = conditionOf(h, bodyStmts);   // header prefix -> body, test -> loop cond
                    if (backOnBranch) cond = negate(cond);         // repeats while the branch-back test holds
                    addLoop(out, atBlk(new DoStmt(new BlockStmt(nl(bodyStmts)), cond), header), header);
                    return exit;
                }
            }

            // pre-tested while: header carries the test; one edge stays in loop, the other exits
            if (h.isConditional() && bodyBlocks.contains(h.fall) != bodyBlocks.contains(h.branch)) {
                Expression cond = conditionOf(h, out);
                int bodyEntry, exit;
                if (bodyBlocks.contains(h.fall)) { bodyEntry = h.fall; exit = h.branch; }
                else { bodyEntry = h.branch; exit = h.fall; cond = negate(cond); }
                ctx.push(new Ctx(true, header, exit, latch, header));   // while: continue -> the header test
                List<Statement> bodyStmts = region(bodyEntry, plus(plus(stop, header), exit));
                ctx.pop();
                addLoop(out, atBlk(buildPreTested(cond, bodyStmts, header, out), header), header);
                return exit;
            }

            // fallback: while(true){ body ... }  (exit via break recovered elsewhere).
            // [bytecode] The body starts AT the header: skipping to a successor (the old behavior)
            // dropped the header's instructions -- for `while (true) { invariant(a && b); ... }`
            // the header opens the invariant's boolean-materialization diamond, and dropping it
            // yielded stray unsupported-opcode markers plus a mangled invariant. The one-shot
            // pass lets region() enter the header once; a walk that cycles back to it stops
            // there (end of the while(true) body = the implicit continue), and explicit jumps
            // back are intercepted as continue edges by loopEdge as before.
            int exit = firstExit(bodyBlocks);
            ctx.push(new Ctx(true, header, exit, latch, header));
            loopEntryPass.add(header);
            infiniteHeaders.add(header);
            List<Statement> bodyStmts;
            try {
                bodyStmts = region(header, plus(plus(stop, header), exit));
            } finally {
                loopEntryPass.remove(header);
                infiniteHeaders.remove(header);
                ctx.pop();
            }
            addLoop(out, atBlk(new WhileStmt(new BooleanLiteralExpr(true), new BlockStmt(nl(bodyStmts))), header), header);
            return exit;
        }

        /**
         * Build a pre-tested loop, recovering {@code for} vs {@code while} from the CRT.
         *
         * <p>{@code for (init; cond; step)} and {@code while} are indistinguishable in bytecode -- both are
         * a test at the header with the step at the tail of the body. The source spans tell them apart: in a
         * {@code for}, the step shares the header's source LINE (it is written in the loop header), whereas a
         * {@code while}'s trailing statement sits on its own later line. When the trailing body statement's
         * span starts on the header's line, it is lifted back into a {@code for} update clause.
         *
         * <p>Falls back to {@code while} whenever spans are absent (no {@code -Xjcov}) or inconclusive, so
         * behaviour is unchanged for corpora compiled without the CRT.
         */
        Statement buildPreTested(Expression cond, List<Statement> bodyStmts, int header, List<Statement> out) {
            Span hs = headerSpan(header);
            if (!hs.isNone() && !bodyStmts.isEmpty()) {
                Statement last = bodyStmts.get(bodyStmts.size() - 1);
                if (last instanceof ExpressionStmt es && last.containsData(SPAN)) {
                    Span ls = last.getData(SPAN);
                    // the step of a `for` is written on the loop-header line, before the body's own lines
                    if (ls != null && !ls.isNone() && ls.beginLine() == hs.beginLine()
                            && bodyStmts.size() > 1 && startsAfter(bodyStmts, hs)) {
                        List<Statement> inner = bodyStmts.subList(0, bodyStmts.size() - 1);
                        return new ForStmt(initClause(hs, out), cond,
                                new NodeList<>(es.getExpression()), new BlockStmt(nl(new ArrayList<>(inner))));
                    }
                }
            }
            return new WhileStmt(cond, new BlockStmt(nl(bodyStmts)));
        }

        /**
         * Reclaim a {@code for}'s init clause. The initializer precedes the loop in bytecode, so it has
         * already been emitted into {@code out}; when its span sits on the loop-header line it was written
         * inside {@code for(...)} in source, and is moved back there. A declaration on its own line belongs
         * to the enclosing scope (it may be read after the loop), so it is left alone.
         */
        NodeList<Expression> initClause(Span hs, List<Statement> out) {
            if (out.isEmpty()) return new NodeList<>();
            Statement prev = out.get(out.size() - 1);
            if (!(prev instanceof ExpressionStmt es) || !prev.containsData(SPAN)) return new NodeList<>();
            Span ps = prev.getData(SPAN);
            if (ps == null || ps.isNone() || ps.beginLine() != hs.beginLine()) return new NodeList<>();
            out.remove(out.size() - 1);
            return new NodeList<>(es.getExpression());
        }

        /** The source span of a loop header's controlling test (its terminator). */
        Span headerSpan(int header) {
            int seqIdx = terminatorOf(header);
            return seqIdx < 0 ? Span.NONE : body.spanAt(seqIdx);
        }

        /** True if every body statement but the last begins on a line after the loop header's line. */
        static boolean startsAfter(List<Statement> bodyStmts, Span header) {
            for (int i = 0; i < bodyStmts.size() - 1; i++) {
                Statement s = bodyStmts.get(i);
                if (!s.containsData(SPAN)) continue;
                Span sp = s.getData(SPAN);
                if (sp != null && !sp.isNone() && sp.beginLine() <= header.beginLine()) return false;
            }
            return true;
        }

        // --- switch (table/lookup) ---
        int emitSwitch(int swBlk, Set<Integer> stop, List<Statement> out) {
            Blk b = cfg.blocks.get(swBlk);
            // selector: instructions before the switch push the value being switched on
            int mark = body.stmts.size();
            for (int i = 0; i < b.instrs.size() - 1; i++) {
                body.pos = b.instrs.get(i);
                body.translate((Instruction) cfg.seq.get(b.instrs.get(i)));
            }
            drainInto(out, mark);
            Expression selector = body.stack.isEmpty() ? new NameExpr("?") : body.stack.pop();

            int follow = dom.ipdom[swBlk];       // merge after the switch == break target

            // group case values that share a target block (case 1: case 2: ...)
            LinkedHashMap<Integer,List<Integer>> byTarget = new LinkedHashMap<>();
            for (int[] c : b.cases) byTarget.computeIfAbsent(c[1], k -> new ArrayList<>()).add(c[0]);
            int defBlk = b.defaultCase;

            Set<Integer> heads = new HashSet<>(byTarget.keySet());
            if (defBlk >= 0) heads.add(defBlk);
            List<Integer> order = new ArrayList<>(heads);
            Collections.sort(order);             // bytecode/block order == source order for javac

            SwitchStmt sw = new SwitchStmt();
            sw.setSelector(selector);
            NodeList<SwitchEntry> entries = new NodeList<>();

            ctx.push(new Ctx(false, -1, follow, -1, -1));   // switch break context
            for (int head : order) {
                Set<Integer> caseStop = new HashSet<>(stop);
                if (follow >= 0) caseStop.add(follow);
                for (int h : heads) if (h != head) caseStop.add(h);   // fallthrough stops at the next case
                List<Statement> bodyStmts = region(head, caseStop);

                SwitchEntry entry = new SwitchEntry();
                if (head != defBlk) {
                    NodeList<Expression> labels = new NodeList<>();
                    for (int v : byTarget.get(head)) labels.add(new IntegerLiteralExpr(String.valueOf(v)));
                    entry.setLabels(labels);
                }   // default entry keeps empty labels
                entry.setStatements(nl(bodyStmts));
                entries.add(entry);
            }
            ctx.pop();
            sw.setEntries(entries);
            out.add(atBlk(sw, swBlk));
            return follow;
        }

        // --- string switch (javac two-level hashCode/equals desugar -> switch(str){ case "a": ... }) ---
        boolean isStringSwitch(Blk b) {
            if (!b.isSwitch() || b.instrs.size() < 2 || b.defaultCase < 0) return false;
            Instruction sel = (Instruction) cfg.seq.get(b.instrs.get(b.instrs.size() - 2));
            if (!(sel instanceof InvokeInstruction inv) || !inv.name().stringValue().equals("hashCode")
                    || !inv.typeSymbol().returnType().descriptorString().equals("I")) return false;
            return cfg.blocks.get(b.defaultCase).isSwitch();   // phase-2 is the real switch on the index temp
        }
        int emitStringSwitch(int p1Blk, Set<Integer> stop, List<Statement> out) {
            Blk p1 = cfg.blocks.get(p1Blk);
            int n1 = p1.instrs.size();
            int mark = body.stmts.size();
            for (int i = 0; i < n1 - 2; i++) {     // everything before `aload X . hashCode` .. lookupswitch, keep X
                body.pos = p1.instrs.get(i);
                body.translate((Instruction) cfg.seq.get(p1.instrs.get(i)));
            }
            drainInto(out, mark);
            Expression selector = body.stack.isEmpty() ? new NameExpr("?") : body.stack.pop();

            int p2Blk = p1.defaultCase;
            Blk p2 = cfg.blocks.get(p2Blk);
            visited[p2Blk] = true;
            // all phase-1 machinery blocks lie between the hashCode switch and phase-2; scan them in order,
            // pairing each `ldc "s"` with the dense index it stores (ldc; equals; ifeq; <idx>; istore tmp)
            Map<Integer,List<String>> idxToStrs = new HashMap<>();
            String lastStr = null; Integer lastInt = null;
            for (int id = p1Blk + 1; id < p2Blk; id++) {
                visited[id] = true;
                for (int si : cfg.blocks.get(id).instrs) {
                    CodeElement e = cfg.seq.get(si);
                    if (e instanceof ConstantInstruction ci) {
                        Object v = ci.constantValue();
                        if (v instanceof String s) lastStr = s;
                        else if (v instanceof Integer iv) lastInt = iv;
                    } else if (e instanceof StoreInstruction) {
                        if (lastStr != null && lastInt != null) idxToStrs.computeIfAbsent(lastInt, k -> new ArrayList<>()).add(lastStr);
                        lastStr = null; lastInt = null;
                    }
                }
            }
            int follow = dom.ipdom[p2Blk];

            // `int r = switch (s) { case "a" -> 1; ... }`: phase 2 produces a VALUE, not statements. Built as
            // a statement switch its arms came out empty and nothing consumed the result, so the whole
            // expression collapsed to whichever constant happened to be left on the stack.
            Integer vMerge = switchValueDiamond(p2Blk);
            if (vMerge != null) {
                body.stack.push(buildSwitchExpr(p2Blk, vMerge, selector,
                        v -> idxToStrs.getOrDefault(v, List.<String>of()).stream()
                                .map(s -> (Expression) strLit(s)).toList()));
                return vMerge;
            }

            LinkedHashMap<Integer,List<Integer>> byTarget = new LinkedHashMap<>();
            for (int[] c : p2.cases) byTarget.computeIfAbsent(c[1], k -> new ArrayList<>()).add(c[0]);
            int defBlk = p2.defaultCase;
            Set<Integer> heads = new HashSet<>(byTarget.keySet());
            if (defBlk >= 0) heads.add(defBlk);
            List<Integer> order = new ArrayList<>(heads);
            Collections.sort(order);

            SwitchStmt sw = new SwitchStmt();
            sw.setSelector(selector);
            NodeList<SwitchEntry> entries = new NodeList<>();
            ctx.push(new Ctx(false, -1, follow, -1, -1));
            for (int head : order) {
                Set<Integer> caseStop = new HashSet<>(stop);
                if (follow >= 0) caseStop.add(follow);
                for (int h : heads) if (h != head) caseStop.add(h);
                List<Statement> bodyStmts = region(head, caseStop);
                SwitchEntry entry = new SwitchEntry();
                if (head != defBlk) {
                    NodeList<Expression> labels = new NodeList<>();
                    for (int idx : byTarget.get(head))
                        for (String s : idxToStrs.getOrDefault(idx, List.of())) labels.add(strLit(s));
                    entry.setLabels(labels);
                }
                entry.setStatements(nl(bodyStmts));
                entries.add(entry);
            }
            ctx.pop();
            sw.setEntries(entries);
            out.add(atBlk(sw, p1Blk));
            return follow;
        }
        // --- switch expression (value-producing switch: int kind = switch(x){ case .. -> v; }) ---
        Integer switchValueDiamond(int swBlk) {
            Blk b = cfg.blocks.get(swBlk);
            if (!b.isSwitch() || b.defaultCase < 0) return null;   // an exhaustive value switch has a default
            int merge = dom.ipdom[swBlk];
            if (merge < 0) return null;
            Set<Integer> heads = new HashSet<>();
            for (int[] c : b.cases) heads.add(c[1]);
            heads.add(b.defaultCase);
            for (int h : heads) {
                if (h == merge) return null;
                if (armIsValue(h, merge, new HashSet<>())) continue;
                // An arm may also contain STATEMENTS and end in `yield` (a block-bodied arm). It still has to
                // converge on the merge. This is the only case that needs the extra test below: a STATEMENT
                // switch whose arms assign a variable also converges, and accepting it built a switch
                // expression whose value nothing consumed, so the whole switch was dropped from the output.
                if (!dom.pdom[h].get(merge) || !mergeConsumesValue(merge)) return null;
            }
            return merge;
        }

        /** True if the merge block opens by consuming a stack value, i.e. the switch really yields one. */
        boolean mergeConsumesValue(int merge) {
            Blk m = cfg.blocks.get(merge);
            if (m.instrs.isEmpty()) return false;
            Instruction first = (Instruction) cfg.seq.get(m.instrs.get(0));
            return first instanceof StoreInstruction
                    || first instanceof ArrayStoreInstruction
                    || (first instanceof ReturnInstruction r && r.opcode() != Opcode.RETURN)
                    || (first instanceof FieldInstruction fi
                        && (fi.opcode() == Opcode.PUTFIELD || fi.opcode() == Opcode.PUTSTATIC))
                    || first instanceof InvokeInstruction;
        }
        void emitSwitchExpr(int swBlk, int merge, List<Statement> out) {
            Blk b = cfg.blocks.get(swBlk);
            int mark = body.stmts.size();
            for (int i = 0; i < b.instrs.size() - 1; i++) {
                body.pos = b.instrs.get(i);
                body.translate((Instruction) cfg.seq.get(b.instrs.get(i)));
            }
            drainInto(out, mark);   // leading statements (e.g. int day = 3;) precede the switch expression
            Expression selector = body.stack.isEmpty() ? new NameExpr("?") : body.stack.pop();
            body.stack.push(buildSwitchExpr(swBlk, merge, selector,
                    v -> List.of(new IntegerLiteralExpr(String.valueOf(v)))));
        }

        /**
         * Build a switch expression over {@code swBlk}'s cases, given its selector and how to spell the label
         * for a case value. Shared with the string form, where the selector is the original string and the
         * labels come from the hash/equals machinery rather than from the dense index the switch tests.
         */
        SwitchExpr buildSwitchExpr(int swBlk, int merge, Expression selector,
                                   java.util.function.IntFunction<List<Expression>> labelsFor) {
            Blk b = cfg.blocks.get(swBlk);
            LinkedHashMap<Integer,List<Integer>> byTarget = new LinkedHashMap<>();
            for (int[] c : b.cases) byTarget.computeIfAbsent(c[1], k -> new ArrayList<>()).add(c[0]);
            int defBlk = b.defaultCase;
            Set<Integer> heads = new HashSet<>(byTarget.keySet());
            heads.add(defBlk);
            List<Integer> order = new ArrayList<>(heads);
            Collections.sort(order);

            NodeList<SwitchEntry> entries = new NodeList<>();
            for (int head : order) {
                SwitchEntry e = new SwitchEntry();
                if (head != defBlk) {
                    NodeList<Expression> labels = new NodeList<>();
                    for (int val : byTarget.get(head)) labels.addAll(labelsFor.apply(val));
                    e.setLabels(labels);
                }
                if (armIsValue(head, merge, new HashSet<>())) {
                    e.setType(SwitchEntry.Type.EXPRESSION);
                    e.setStatements(new NodeList<>(new ExpressionStmt(evalArm(head, merge))));  // case .. -> v
                } else {
                    // A block-bodied arm: its statements are structured normally and the value it leaves
                    // becomes the `yield` that supplies the switch expression's result.
                    List<Statement> armStmts = region(head, Set.of(merge));
                    if (!body.stack.isEmpty()) armStmts.add(new YieldStmt(body.stack.pop()));
                    e.setType(SwitchEntry.Type.BLOCK);
                    e.setStatements(new NodeList<>(new BlockStmt(nl(armStmts))));
                }
                entries.add(e);
            }
            return new SwitchExpr(selector, entries);
        }

        // --- pattern switch (SwitchBootstraps.typeSwitch: type patterns, case null, string/int constants) ---

        /** One recovered case of a pattern switch: its labels (type pattern / constant / null) and body. */
        static final class PatCase {
            int target;                              // the lookupswitch target block id
            boolean isDefault;
            boolean nullLabel;                       // matched by lookupswitch value -1
            final List<Expression> constLabels = new ArrayList<>();   // string / int constant labels
            int bindSlot = -1;                       // type-pattern binding local slot (-1 if not a type pattern)
            int bindPos = -1;                        // seq-index of the binding astore (for LVT name resolution)
            Type bindType;                           // type-pattern type (from checkcast)
            String bindName;                         // resolved binding name (phase 2)
            int bodyEntry = -1;                      // block where the case body begins (after folding the bind)
            // guarded pattern (`case T bind when <guard>`): the guard is a conditional after the bind whose
            // failing edge restarts the typeSwitch with the next index.
            boolean guarded;
            int guardBlk = -1;                       // conditional block holding the guard test (== target)
            int restartBlk = -1;                     // the restart edge (istore R; goto swBlk) to consume
            boolean guardNegate;                     // guard must be negated (restart is on the branch-taken side)
            // record pattern (`case P(int x, int y)`): the components deconstructed out of the bound record,
            // and the chain of blocks doing so, which is consumed here rather than emitted as statements.
            Comp deconRoot;                                       // component tree, filled in phase 1
            NodeList<com.github.javaparser.ast.expr.PatternExpr> components;  // resolved, phase 2
            final Set<Integer> chainBlks = new HashSet<>();
            boolean isRecordPattern;                              // set in phase 1, before names are known
            int bodyLead;                                         // instrs to skip if the arm shares the bind block
        }

        /** The SwitchBootstraps.typeSwitch invokedynamic feeding block b's switch, or null. */
        InvokeDynamicInstruction typeSwitchIndy(Blk b) {
            if (!b.isSwitch() || b.instrs.size() < 2) return null;
            if (cfg.seq.get(b.instrs.get(b.instrs.size() - 2)) instanceof InvokeDynamicInstruction indy) {
                try {
                    var ref = indy.invokedynamic().bootstrap().bootstrapMethod().reference();
                    if (ref.owner().asInternalName().equals("java/lang/runtime/SwitchBootstraps")
                            && indy.name().stringValue().equals("typeSwitch")) return indy;
                } catch (RuntimeException ignore) { }
            }
            return null;
        }

        Instruction insAt(Blk b, int k) { return (Instruction) cfg.seq.get(b.instrs.get(k)); }

        /**
         * The merge block of a switch whose post-dominator is the method exit, which happens when an arm
         * completes ABRUPTLY -- {@code case Integer i -> { throw ... }} never reaches the merge, so nothing
         * post-dominates the switch. The arms that do converge still meet where the switch's value is
         * consumed, so an arm's post-dominator that opens by consuming a value is that block.
         *
         * <p>This distinguishes the merge from a block INSIDE an abrupt arm: the throwing arm's own
         * post-dominator is its throw block, which begins by constructing the exception rather than
         * consuming a value.
         */
        int abruptArmMerge(Blk sw) {
            Set<Integer> heads = new HashSet<>();
            for (int[] c : sw.cases) if (c[1] >= 0) heads.add(c[1]);
            if (sw.defaultCase >= 0) heads.add(sw.defaultCase);
            int best = -1;
            for (int h : heads) {
                int m = dom.ipdom[h];
                if (m >= 0 && mergeConsumesValue(m) && (best < 0 || m < best)) best = m;
            }
            return best;
        }

        /** True if {@code blk} is a typeSwitch restart edge: it stores the restart index R and jumps to swBlk. */
        boolean isRestart(int blk, int swBlk, int rSlot) {
            if (blk < 0) return false;
            Blk rb = cfg.blocks.get(blk);
            if (rb.gotoTarget != swBlk) return false;
            for (int idx : rb.instrs)
                if (cfg.seq.get(idx) instanceof StoreInstruction st && st.slot() == rSlot) return true;
            return false;
        }

        /**
         * Recover a {@code switch} over {@code SwitchBootstraps.typeSwitch}: type patterns, {@code case null},
         * string/int constant labels, and guarded ({@code when}) patterns, in both statement and switch-expression
         * form. {@code setupBlk} holds the selector + temp/restart setup (== swBlk for a plain switch, or the
         * preheader for a guarded switch whose restart back-edge makes swBlk a loop header). Returns the merge
         * block on success, or null to decline (caller falls back to the generic handling).
         */
        Integer tryPatternSwitch(int setupBlk, int swBlk, Set<Integer> stop, List<Statement> out) {
            Blk sw = cfg.blocks.get(swBlk);
            Blk setup = cfg.blocks.get(setupBlk);
            InvokeDynamicInstruction indy = typeSwitchIndy(sw);
            if (indy == null) return null;
            int merge = dom.ipdom[swBlk];
            if (merge < 0) merge = abruptArmMerge(sw);
            if (merge < 0) return null;

            // In swBlk the two instructions before the invokedynamic are `aload T; iload R`.
            int indyIdx = sw.instrs.size() - 2;
            if (indyIdx < 2) return null;
            if (!(insAt(sw, indyIdx - 2) instanceof LoadInstruction loadT)) return null;
            if (!(insAt(sw, indyIdx - 1) instanceof LoadInstruction loadR)) return null;
            int tSlot = loadT.slot(), rSlot = loadR.slot();

            // In setupBlk find `astore T; iconst_0; istore R`; the selector is whatever was stored into T.
            int aIdx = -1;
            for (int i = 0; i + 2 < setup.instrs.size(); i++) {
                if (insAt(setup, i) instanceof StoreInstruction s1 && s1.slot() == tSlot
                        && isIntConst(cfg.seq.get(setup.instrs.get(i + 1)), 0)
                        && insAt(setup, i + 2) instanceof StoreInstruction s2 && s2.slot() == rSlot) { aIdx = i; break; }
            }
            if (aIdx < 0) return null;
            int suffixStart = aIdx;                  // stop translating here; the selector is left on the stack
            if (aIdx >= 3
                    && insAt(setup, aIdx - 1) instanceof StackInstruction sk && sk.opcode() == Opcode.POP
                    && insAt(setup, aIdx - 2) instanceof InvokeInstruction rn && rn.name().stringValue().equals("requireNonNull")
                    && Body.isDup(cfg.seq.get(setup.instrs.get(aIdx - 3))))
                suffixStart = aIdx - 3;              // selector was null-checked (no `case null`)

            List<LoadableConstantEntry> labels;
            try { labels = indy.invokedynamic().bootstrap().arguments(); } catch (RuntimeException e) { return null; }

            // ---- Phase 1: structural validation & parsing (no body-state mutation) ----
            LinkedHashMap<Integer,List<Integer>> byTarget = new LinkedHashMap<>();
            for (int[] c : sw.cases) byTarget.computeIfAbsent(c[1], k -> new ArrayList<>()).add(c[0]);
            int defBlk = sw.defaultCase;
            Set<Integer> targetSet = new HashSet<>(byTarget.keySet());
            if (defBlk >= 0) targetSet.add(defBlk);
            List<Integer> order = new ArrayList<>(targetSet);
            Collections.sort(order);                 // block order == source order for javac

            List<PatCase> cases = new ArrayList<>();
            for (int tb : order) {
                PatCase pc = new PatCase();
                pc.target = tb;
                pc.isDefault = (tb == defBlk);
                boolean typePattern = false;
                for (int v : byTarget.getOrDefault(tb, List.of())) {
                    if (v == -1) { pc.nullLabel = true; continue; }
                    if (v < 0 || v >= labels.size()) return null;
                    LoadableConstantEntry le = labels.get(v);
                    if (le instanceof ClassEntry) typePattern = true;
                    else if (le instanceof StringEntry se) pc.constLabels.add(strLit(se.stringValue()));
                    else if (le instanceof IntegerEntry ie) pc.constLabels.add(constEntry(ie));
                    else return null;                // enum / dynamic constant labels: decline
                }
                // `case null, default` (null and default share a block) not yet modeled -> decline
                if (pc.nullLabel && pc.isDefault) return null;
                if (typePattern) {
                    if (byTarget.getOrDefault(tb, List.of()).size() != 1) return null;   // no combined type + other
                    if (!parseTypePatternHead(tb, pc)) return null;
                    // A record pattern deconstructs the bound value; try that before reading the same
                    // conditional as a `when` guard, which is what it would otherwise look like.
                    if (!parseRecordPattern(pc, swBlk, rSlot)
                            && !parseGuard(pc, swBlk, rSlot)) return null;
                } else {
                    pc.bodyEntry = tb;
                }
                cases.add(pc);
            }

            // Every edge BACK into swBlk must be a recognized guard restart; an unhandled one means a restart
            // shape we do not model (record-pattern deconstruction) -> decline. A back-edge is identified by
            // swBlk dominating its source. Comparing the predecessor against setupBlk instead was wrong
            // whenever the setup shares a block with the switch, which is the unguarded case: the normal
            // entry then comes from whatever precedes that block and was mistaken for an unknown restart.
            Set<Integer> knownRestarts = new HashSet<>();
            for (PatCase pc : cases) if (pc.guarded || pc.isRecordPattern) knownRestarts.add(pc.restartBlk);
            for (int p : sw.preds)
                if (dom.dominates(swBlk, p) && !knownRestarts.contains(p)) return null;

            // all validation done; skip the 3-instr bind prefix of each type-pattern case from here on.
            // A record pattern's whole deconstruction chain is folded into the label instead, so its blocks
            // are marked consumed rather than translated.
            for (PatCase pc : cases) {
                if (pc.bindSlot >= 0) skipLead.put(pc.target, 3);
                if (pc.isRecordPattern) {
                    visited[pc.target] = true;
                    for (int b : pc.chainBlks) visited[b] = true;
                    markRestartVisited(pc.restartBlk, swBlk);
                    if (pc.bodyLead > 0) skipLead.put(pc.bodyEntry, pc.bodyLead);
                }
            }

            // statement vs switch-expression: expression iff every arm is a pure value arm to the merge
            boolean isValue = !cases.isEmpty();
            for (PatCase pc : cases)
                if (pc.guarded || pc.bodyEntry == merge || !armIsValue(pc.bodyEntry, merge, new HashSet<>())) { isValue = false; break; }

            // ---- Phase 2: emit ----
            int mark = body.stmts.size();
            for (int i = 0; i < suffixStart; i++) {          // user code + selector expression
                body.pos = setup.instrs.get(i);
                body.translate(insAt(setup, i));
            }
            drainInto(out, mark);
            Expression selector = body.stack.isEmpty() ? new NameExpr("?") : body.stack.pop();
            if (setupBlk != swBlk) visited[swBlk] = true;    // the switch head itself is consumed here

            if (isValue) {
                NodeList<SwitchEntry> entries = new NodeList<>();
                for (PatCase pc : cases) {
                    resolveBinding(pc); resolveComponents(pc);
                    Expression v = evalArm(pc.bodyEntry, merge);
                    SwitchEntry e = new SwitchEntry();
                    if (!pc.isDefault) e.setLabels(patLabels(pc));
                    e.setType(SwitchEntry.Type.EXPRESSION);
                    e.setStatements(new NodeList<>(new ExpressionStmt(v)));
                    entries.add(e);
                }
                body.stack.push(new SwitchExpr(selector, entries));
                return merge;
            }

            // statement switch
            Set<Integer> caseStop = new HashSet<>(stop);
            caseStop.add(merge);
            for (PatCase pc : cases) { caseStop.add(pc.bodyEntry); if (pc.guarded) caseStop.add(pc.guardBlk); }
            NodeList<SwitchEntry> entries = new NodeList<>();
            for (PatCase pc : cases) {
                resolveBinding(pc); resolveComponents(pc);
                SwitchEntry e = new SwitchEntry();
                if (!pc.isDefault) e.setLabels(patLabels(pc));
                if (pc.guarded) {                            // `case T bind when <guard>`
                    visited[pc.guardBlk] = true;
                    List<Statement> gpre = new ArrayList<>();
                    Expression guard = conditionOf(cfg.blocks.get(pc.guardBlk), gpre);
                    if (!gpre.isEmpty()) return null;        // guard with side effects: unsupported
                    if (pc.guardNegate) guard = negate(guard);
                    e.setGuard(guard);
                    markRestartVisited(pc.restartBlk, swBlk);
                }
                Set<Integer> cs = new HashSet<>(caseStop);
                cs.remove(pc.bodyEntry);                     // don't stop at our own entry
                List<Statement> bodyStmts = region(pc.bodyEntry, cs);
                if (!bodyStmts.isEmpty() && bodyStmts.get(bodyStmts.size() - 1) instanceof BreakStmt bs
                        && bs.getLabel().isEmpty()) bodyStmts.remove(bodyStmts.size() - 1);  // arrows don't fall through
                if (bodyStmts.size() == 1 && bodyStmts.get(0) instanceof ExpressionStmt) {
                    e.setType(SwitchEntry.Type.EXPRESSION);
                    e.setStatements(nl(bodyStmts));
                } else if (bodyStmts.size() == 1 && bodyStmts.get(0) instanceof ThrowStmt) {
                    e.setType(SwitchEntry.Type.THROWS_STATEMENT);
                    e.setStatements(nl(bodyStmts));
                } else {
                    e.setType(SwitchEntry.Type.BLOCK);
                    e.setStatements(new NodeList<>(new BlockStmt(nl(bodyStmts))));
                }
                entries.add(e);
            }
            SwitchStmt swStmt = new SwitchStmt();
            swStmt.setSelector(selector);
            swStmt.setEntries(entries);
            out.add(atBlk(swStmt, setupBlk));
            return merge;
        }

        /**
         * Recover a record pattern, {@code case P(int x, int y)}, from its deconstruction chain.
         *
         * <p>After the type-pattern head has bound the record instance, javac emits one group per component:
         *
         * <pre>
         *   aload REC                                  // the bound record
         *   invokevirtual x()                          // its own block: protected, to raise MatchException
         *   istore TMP; iload TMP; istore COPY
         *   iconst_1; ifeq RESTART                      // always-true match test of the state machine
         *   iload TMP; istore BIND                      // the component binding
         * </pre>
         *
         * ...and after the last group jumps to the arm body. Every failing edge targets the same restart
         * block, which re-enters the switch with the next index -- which is why the switch block is a loop
         * header and, left unrecognised, came out as a {@code while (true)} wrapping the whole method.
         *
         * <p>The component binding names come from the LocalVariableTable. Returns false for anything that
         * does not match this exact shape, including nested record patterns, leaving the caller's existing
         * handling untouched.
         */
        boolean parseRecordPattern(PatCase pc, int swBlk, int rSlot) {
            if (pc.bindSlot < 0) return false;                    // needs a type-pattern head
            Decon d = walkComponents(pc.target, pc.bindSlot);
            // in a switch, every failing edge re-enters the typeSwitch with the next index
            if (d == null || !isRestart(d.failTarget, swBlk, rSlot)) return false;
            // Phase 1 must not touch body state, so only the slots are recorded here; the names and types are
            // resolved in phase 2 by resolveComponents.
            pc.deconRoot = d.root;
            pc.chainBlks.addAll(d.blks);
            pc.restartBlk = d.failTarget;
            pc.bodyEntry = d.bodyEntry;
            pc.bodyLead = d.bodyLead;
            pc.isRecordPattern = true;
            return true;
        }

        /**
         * A record-pattern deconstruction chain: the component bindings, the blocks it occupies, and where
         * the pattern's body resumes once the chain is folded into a pattern.
         */
        static final class Decon {
            final Comp root = new Comp();
            final Set<Integer> blks = new HashSet<>();
            int failTarget = -1;   // the shared target of every component's failing edge
            int bodyEntry = -1;
            int bodyLead;          // instructions to skip at bodyEntry, when the body shares the last bind block
        }

        /** One component of a deconstruction: a plain binding, or a nested pattern with components of its own. */
        static final class Comp {
            int slot = -1, pos = -1;                       // the binding store, for LVT name and type
            Type nestedType;                               // non-null when the component is itself a pattern
            final List<Comp> parts = new ArrayList<>();
        }

        /**
         * Walk the per-component chain javac emits to deconstruct a record, starting from a head block that
         * ends by loading the record instance. Each component reads:
         *
         * <pre>
         *   invokevirtual x()                  // its own block: protected, to raise MatchException
         *   istore TMP; iload TMP; istore COPY
         *   iconst_1; ifeq FAIL                 // always-true match test of the state machine
         *   iload TMP; istore BIND              // the component binding
         * </pre>
         *
         * <p>...then either loads the record again for the next component or leaves for the body. Every
         * failing edge shares one target, which the caller validates: a switch restarts its typeSwitch, an
         * instanceof jumps to its own false edge. Returns null for anything else, including nested patterns.
         */
        Decon walkComponents(int headBlk, int rootSlot) {
            Blk head = cfg.blocks.get(headBlk);
            int n = head.instrs.size();
            if (n < 4 || !(insAt(head, n - 1) instanceof LoadInstruction ld) || ld.slot() != rootSlot) return null;

            Decon d = new Decon();
            Map<Integer,Comp> owners = new HashMap<>();
            owners.put(rootSlot, d.root);
            int rec = rootSlot, cur = head.fall;
            while (cur >= 0 && cur < cfg.blocks.size() && d.blks.size() < 96) {
                Comp owner = owners.get(rec);
                if (owner == null) return null;                   // a chain on a record we never bound
                Blk call = cfg.blocks.get(cur);                   // the accessor invocation
                if (call.instrs.isEmpty()) return null;
                Instruction inv = insAt(call, call.instrs.size() - 1);
                if (!(inv instanceof InvokeInstruction acc) || acc.typeSymbol().parameterCount() != 0) return null;
                d.blks.add(cur);
                int testBlk = call.fall;
                if (testBlk < 0 || testBlk >= cfg.blocks.size()) return null;
                Blk test = cfg.blocks.get(testBlk);               // istore/iload/istore + always-true ifeq
                if (!test.isConditional() || test.last.opcode() != Opcode.IFEQ) return null;
                if (d.failTarget >= 0 && d.failTarget != test.branch) return null;
                d.failTarget = test.branch;
                d.blks.add(testBlk);
                // A component that is itself a record pattern is type-tested for real, and its own components
                // follow later in the chain rather than inline. A plain binding gets the always-true flag.
                int ti = test.instrs.size() - 2;
                Type nested = ti >= 0 && insAt(test, ti) instanceof TypeCheckInstruction nt
                        && nt.opcode() == Opcode.INSTANCEOF ? type(nt.type().asSymbol()) : null;

                int bindBlk = test.fall;
                if (bindBlk < 0 || bindBlk >= cfg.blocks.size()) return null;
                Blk bind = cfg.blocks.get(bindBlk);
                // `iload TMP; istore BIND`, then the next group's `aload REC`, a jump, or the body itself
                if (bind.instrs.size() < 2) return null;
                if (!(insAt(bind, 0) instanceof LoadInstruction)) return null;
                if (!(insAt(bind, 1) instanceof StoreInstruction bs)) return null;
                Comp comp = new Comp();
                comp.slot = bs.slot(); comp.pos = bind.instrs.get(1); comp.nestedType = nested;
                owner.parts.add(comp);
                if (nested != null) owners.put(comp.slot, comp);

                int last = bind.instrs.size() - 1;
                // the trailing load names the record the next group deconstructs, which is how javac moves on
                // to a nested component after binding every component of the enclosing one
                if (last >= 2 && insAt(bind, last) instanceof LoadInstruction nl && owners.containsKey(nl.slot())) {
                    d.blks.add(bindBlk);
                    rec = nl.slot();
                    cur = bind.fall;
                    continue;
                }
                if (bind.instrs.size() == 2) {
                    d.blks.add(bindBlk);
                    d.bodyEntry = bind.gotoTarget >= 0 ? bind.gotoTarget : bind.fall;
                } else if (bind.instrs.size() == 3 && bind.gotoTarget >= 0) {
                    d.blks.add(bindBlk);
                    d.bodyEntry = bind.gotoTarget;                // a switch arm jumps away from the chain
                } else {
                    // an instanceof's then-arm continues in the very block that binds the last component,
                    // so the block stays live and only its binding prefix is skipped
                    d.bodyEntry = bindBlk;
                    d.bodyLead = 2;
                }
                break;
            }
            // every nested component must have been filled in by a later group
            for (Comp c : owners.values()) if (c.nestedType != null && c.parts.isEmpty()) return null;
            return d.bodyEntry >= 0 && !d.root.parts.isEmpty() && d.failTarget >= 0 ? d : null;
        }

        /**
         * Build a record pattern's component list, naming each binding from the LocalVariableTable at its
         * store and recursing into nested patterns. Marks the bindings declared, so the chain's own stores are
         * not re-emitted as statements.
         */
        NodeList<com.github.javaparser.ast.expr.PatternExpr> componentPatterns(Comp owner) {
            NodeList<com.github.javaparser.ast.expr.PatternExpr> out = new NodeList<>();
            for (Comp c : owner.parts) {
                if (c.nestedType != null) {
                    out.add(new RecordPatternExpr(new NodeList<>(), c.nestedType, componentPatterns(c)));
                } else {
                    body.pos = c.pos;
                    out.add(new TypePatternExpr(new NodeList<>(), body.localType(c.slot),
                            new SimpleName(body.localName(c.slot))));
                }
                body.declared.add(body.varKey(c.slot));
            }
            return out;
        }

        /** Phase 2: name and type each record component from the LocalVariableTable at its binding store. */
        void resolveComponents(PatCase pc) {
            if (pc.deconRoot != null) pc.components = componentPatterns(pc.deconRoot);
        }

        /**
         * Detect a guarded pattern: the case's body block is a conditional whose failing edge is a restart
         * (istore R; goto swBlk). Splits the case into its guard test and the true arm. For an unguarded
         * pattern this leaves {@code pc.bodyEntry} at the block start. Declines (false) on odd restart shapes.
         */
        boolean parseGuard(PatCase pc, int swBlk, int rSlot) {
            Blk gb = cfg.blocks.get(pc.target);
            if (!gb.isConditional()) return true;                        // no guard
            boolean fRestart = isRestart(gb.fall, swBlk, rSlot);
            boolean brRestart = isRestart(gb.branch, swBlk, rSlot);
            if (!fRestart && !brRestart) return true;                    // conditional but not a guard restart
            if (fRestart && brRestart) return false;                     // both restart: unexpected
            if (!guardIsClean(gb)) return false;                         // record-pattern deconstruction: decline
            pc.guarded = true;
            pc.guardBlk = pc.target;
            pc.restartBlk = fRestart ? gb.fall : gb.branch;
            pc.bodyEntry = fRestart ? gb.branch : gb.fall;               // the arm is the non-restart edge
            // conditionOf returns the fall-through condition; the guard is the condition to reach the arm,
            // so negate when the arm is the branch target (i.e. the restart is on the fall edge).
            pc.guardNegate = fRestart;
            return true;
        }

        /**
         * A real {@code when} guard evaluates a side-effect-free condition after the bind. The 3-instruction
         * bind prefix aside, the guard block must contain no stores or void calls before its branch; anything
         * else (accessor calls + binds) signals record-pattern deconstruction, which is not a guard.
         */
        boolean guardIsClean(Blk gb) {
            for (int i = 3; i < gb.instrs.size() - 1; i++) {
                Instruction in = insAt(gb, i);
                if (in instanceof StoreInstruction || in instanceof ArrayStoreInstruction
                        || in instanceof IncrementInstruction
                        || (in instanceof FieldInstruction fi && (fi.opcode() == Opcode.PUTFIELD || fi.opcode() == Opcode.PUTSTATIC))
                        || (in instanceof InvokeInstruction inv && inv.typeSymbol().returnType().descriptorString().equals("V")))
                    return false;
            }
            return true;
        }

        /** Consume a restart edge block (istore R; goto swBlk) so the structurer's coverage check is satisfied. */
        void markRestartVisited(int restartBlk, int swBlk) {
            if (restartBlk >= 0) visited[restartBlk] = true;
        }

        /**
         * Validate a type-pattern case head: the block begins with {@code [aload T; checkcast TYPE; astore BIND]}.
         * The body may be inlined in the same block (javac) or split off via a goto (some compilers); either way
         * the 3-instruction bind prefix is skipped via {@code skipLead} and the body is structured from this block.
         */
        boolean parseTypePatternHead(int tb, PatCase pc) {
            Blk cb = cfg.blocks.get(tb);
            if (cb.instrs.size() < 3) return false;
            if (!(insAt(cb, 0) instanceof LoadInstruction)) return false;                          // aload T
            if (!(insAt(cb, 1) instanceof TypeCheckInstruction tc) || tc.opcode() != Opcode.CHECKCAST) return false;
            if (!(insAt(cb, 2) instanceof StoreInstruction st)) return false;                       // astore BIND
            pc.bindType = type(tc.type().asSymbol());
            pc.bindSlot = st.slot();
            pc.bindPos = cb.instrs.get(2);
            pc.bodyEntry = tb;                        // body begins here, after the bind prefix (skipLead=3)
            return true;
        }

        /** Resolve a type-pattern binding's name (LVT at the astore) and mark it declared. */
        void resolveBinding(PatCase pc) {
            if (pc.bindSlot < 0) return;
            // a record pattern binds its components, not the record; the instance temp has no source name
            if (pc.isRecordPattern) return;
            body.pos = pc.bindPos;
            pc.bindName = body.localName(pc.bindSlot);
            body.declared.add(body.varKey(pc.bindSlot));
        }

        /** Build the label list for a case: `case null`, constants, and/or a type pattern. */
        NodeList<Expression> patLabels(PatCase pc) {
            NodeList<Expression> lbls = new NodeList<>();
            if (pc.nullLabel) lbls.add(new NullLiteralExpr());
            lbls.addAll(pc.constLabels);
            // a record pattern names its components instead of binding the record itself
            if (pc.isRecordPattern) {
                lbls.add(new RecordPatternExpr(new NodeList<>(), pc.bindType, pc.components));
            } else if (pc.bindName != null) {
                lbls.add(new TypePatternExpr(new NodeList<>(), pc.bindType, new SimpleName(pc.bindName)));
            }
            return lbls;
        }

        // --- try / catch / finally ---
        /**
         * Two try statements can protect the same first instruction:
         * {@code try { try { ... } catch (A a) { ... } } catch (B b) { ... }} produces two exception-table
         * entries with the same start. They are siblings in the table but nested in the source, and emitting
         * them as sibling catch clauses on one try is not just inexact -- when the types are the same it does
         * not compile at all ("exception RuntimeException has already been caught").
         *
         * <p>The signal is containment of a HANDLER, not of a range: the outer entry's protected range covers
         * the inner entry's handler, because the source has the inner catch sitting inside the outer try.
         * True sibling clauses instead share one range exactly, so grouping by range end separates the cases.
         *
         * <p>Returns the entries grouped innermost-first; a single group when there is no nesting. A
         * catch-all (finally-desugaring) entry participates in the grouping like any other: an inner
         * {@code try/finally} wrapped by an outer {@code catch} protects a strictly smaller range and
         * must be recovered at its own level, or the finally would wrongly run AFTER the outer arm.
         * (Same-range catch+finally entries share an end and stay in one group regardless.)
         */
        List<List<Exc>> nestLevels(List<Exc> excs) {
            List<List<Exc>> one = List.of(new ArrayList<>(excs));
            TreeMap<Integer,List<Exc>> byEnd = new TreeMap<>();
            for (Exc e : excs) byEnd.computeIfAbsent(e.endBlkId(), k -> new ArrayList<>()).add(e);
            if (byEnd.size() < 2) return one;
            List<List<Exc>> levels = new ArrayList<>(byEnd.values());
            for (int i = 0; i + 1 < levels.size(); i++) {
                int innerH = levels.get(i).get(0).handlerBlk();
                for (Exc o : levels.get(i + 1))
                    if (innerH < o.startBlk() || innerH >= o.endBlkId()) return one;
            }
            return levels;
        }

        int emitTry(int startBlk, Set<Integer> stop, List<Statement> out) {
            emittedTries.add(startBlk);
            List<List<Exc>> levels = nestLevels(triesByStart.get(startBlk));
            return emitTryLevel(startBlk, levels, levels.size() - 1, stop, out);
        }

        /** Emit nesting level {@code li} (outermost last); its body is level {@code li - 1}, or the region. */
        int emitTryLevel(int startBlk, List<List<Exc>> levels, int li, Set<Integer> stop, List<Statement> out) {
            List<Exc> excs = levels.get(li);

            // partition into typed catches (grouped by handler for multi-catch) and a finally handler.
            // A catch-all entry is a finally candidate only when it has the handler shape AND either the
            // protected range always throws (no normal-exit copy exists) or the inlined normal-path copy
            // is found and consumed; otherwise it stays a plain catch clause (e.g. the try-with-resources
            // close/suppress handler, whose normal-path copy is a different instruction sequence).
            LinkedHashMap<Integer,List<Exc>> byHandler = new LinkedHashMap<>();
            FinallyShape fshape = null;
            int finallyBlk = -1, finallyEndId = -1;
            for (Exc e : excs) {
                FinallyShape s;
                if (e.type() == null && finallyBlk < 0 && (s = finallyShape(e.handlerBlk())) != null) {
                    fshape = s; finallyBlk = e.handlerBlk(); finallyEndId = e.endBlkId();
                } else {
                    byHandler.computeIfAbsent(e.handlerBlk(), k -> new ArrayList<>()).add(e);
                }
            }

            // A protected range that ALWAYS throws has no normal-exit copy: its recorded end abuts or
            // overlaps the handler itself (javac's catch-all range extends over the finally's own body,
            // minus the rethrow). Consuming "the copy" there would eat the handler's body.
            boolean noCopy = fshape != null
                    && finallyEndId >= fshape.hb() && finallyEndId <= fshape.hEnd();
            // catch+finally: the finally's protected range extends over the typed arms, and each arm
            // carries its own inlined copy at its exit. The copy at the range's end belongs to an ARM,
            // not to the try's normal exit -- it is skipped wholesale (chainExit path below) and the
            // arm-tail copies are stripped statement-wise after the arms are built.
            boolean armsBelow = !noCopy && fshape != null
                    && levels.stream().flatMap(List::stream).anyMatch(e -> e.type() != null);

            // Plain try/finally: fold the inlined normal-path copy BEFORE committing to the finally --
            // a mismatch demotes the handler back to a plain catch-all clause (safe degradation; the
            // try-with-resources close/suppress handler is demoted here, its "copy" being a plain
            // close() call with a different instruction sequence).
            int copyFollow = Integer.MIN_VALUE;
            if (fshape != null && !noCopy && !armsBelow) {
                copyFollow = consumeFinallyCopy(fshape, finallyEndId);
                if (copyFollow == Integer.MIN_VALUE) {
                    byHandler.computeIfAbsent(finallyBlk, k -> new ArrayList<>())
                            .add(new Exc(startBlk, finallyEndId, finallyBlk, null));
                    fshape = null; finallyBlk = -1; finallyEndId = -1;
                }
            }

            Set<Integer> handlerBlks = new HashSet<>(byHandler.keySet());
            if (fshape != null) {
                for (int b = fshape.hb(); b <= fshape.hEnd(); b++) handlerBlks.add(b);
            }

            int catchEndId = byHandler.isEmpty() ? finallyEndId : byHandler.values().iterator().next().get(0).endBlkId();
            int follow;
            Set<Integer> bodyStop = new HashSet<>(stop);
            bodyStop.addAll(handlerBlks);
            // The consumed normal-path copy starts right at the protected range's end; the body region
            // must stop there (its blocks are consumed, but the region walk does not check visited).
            if (fshape != null && !noCopy) bodyStop.add(finallyEndId);

            List<Statement> finallyBody = null;
            if (finallyBlk >= 0) {
                // Suppress this finally's SATELLITE protection regions: javac emits separate
                // exception-table entries for each catch arm's range and for the handler itself, all
                // pointing at the same handler. They are artifacts of the one source finally --
                // intercepting them as try starts would wrap the arms (or the finally body itself)
                // in phantom try/finally statements.
                final int fh = finallyBlk;
                for (var it = triesByStart.entrySet().iterator(); it.hasNext(); ) {
                    var en = it.next();
                    if (en.getKey() != startBlk) {
                        en.getValue().removeIf(e -> e.type() == null && e.handlerBlk() == fh);
                        if (en.getValue().isEmpty()) it.remove();
                    }
                }
                if (noCopy) {
                    // The try's continuation, if any, is where its typed arms reconverge; the scan is
                    // capped before the handler so the handler's own blocks can't pose as the follow.
                    follow = tryFollow(startBlk, Math.min(finallyEndId, fshape.hb()), handlerBlks);
                } else if (armsBelow) {
                    // The chain at the range's end is the first arm's copy + exit jump; where it lands
                    // is where the try reconverges. The chain itself is consumed (it is that arm's tail).
                    follow = chainExit(finallyEndId);
                    markChainVisited(finallyEndId, follow);
                    bodyStop.add(finallyEndId);
                } else {
                    follow = copyFollow;
                }
                finallyBody = extractFinally(fshape);
            } else {
                follow = tryFollow(startBlk, catchEndId, handlerBlks);
                // Where the CRT records the clauses' extents, the try statement ends after the LAST of them.
                // That is authoritative: the inferred follow is where the try and its handlers reconverge,
                // which can land inside a clause whose body contains a conditional and truncate it.
                int crtFollow = -1;
                for (int hb : byHandler.keySet()) {
                    Integer ce = cfg.catchEnd.get(hb);
                    if (ce != null && (crtFollow < 0 || ce > crtFollow)) crtFollow = ce;
                }
                if (crtFollow >= 0) follow = crtFollow;
            }
            if (follow >= 0) bodyStop.add(follow);

            // An outer level's body is the try nested inside it, not a straight-line region: both start at
            // the same block, so the region walk would translate the inner try's protected range again.
            List<Statement> tryBody;
            if (li > 0) {
                tryBody = new ArrayList<>();
                int innerFollow = emitTryLevel(startBlk, levels, li - 1, bodyStop, tryBody);
                if (innerFollow >= 0 && !bodyStop.contains(innerFollow))
                    tryBody.addAll(region(innerFollow, bodyStop));
            } else {
                tryBody = region(startBlk, bodyStop);
            }

            // A `return` crossing the finally: javac stashes the value in a temp, runs the inlined copy,
            // then loads and returns. The copy consumption above left the load;return remainder in the
            // follow block -- but the stash temp is declared INSIDE the try body, so the return must be
            // pulled back in with it. When the shape is exactly `var TMP = <expr>` + `return TMP`, the
            // original source `return <expr>;` is recovered outright (also fixing the temp's `var` type,
            // which the Laurel frontend cannot use).
            if (fshape != null && follow >= 0 && skipLead.containsKey(follow)
                    && cfg.blocks.get(follow).succ.isEmpty()) {
                Set<Integer> tailStop = new HashSet<>(bodyStop);
                tailStop.remove(follow);   // bodyStop contains the follow itself
                List<Statement> tail = region(follow, tailStop);
                if (tail.size() == 1 && tail.get(0) instanceof ReturnStmt ret
                        && ret.getExpression().orElse(null) instanceof NameExpr tmp
                        && !tryBody.isEmpty()
                        && tryBody.get(tryBody.size() - 1) instanceof ExpressionStmt es
                        && es.getExpression() instanceof VariableDeclarationExpr vd
                        && vd.getVariables().size() == 1
                        && vd.getVariable(0).getNameAsString().equals(tmp.getNameAsString())
                        && vd.getVariable(0).getInitializer().isPresent()) {
                    Statement newRet = new ReturnStmt(vd.getVariable(0).getInitializer().get().clone());
                    if (es.containsData(SPAN)) newRet.setData(SPAN, es.getData(SPAN));
                    tryBody.set(tryBody.size() - 1, newRet);
                } else {
                    tryBody.addAll(tail);
                }
                follow = -1;
            }

            // `try { return f(); }` leaves its value on the operand stack at the end of the body: javac ends
            // the protected range BEFORE the ireturn, since a return cannot throw, so the terminator lands in
            // the follow block. Emitted there it becomes `try {} catch (...) {...} return f();` -- the call the
            // try existed to guard moved outside it, where the catch can no longer see it. The terminator
            // belongs to the body, which is also where the source wrote it.
            // Only for a TYPED catch. A catch-all here is the monitorexit guard of a synchronized statement,
            // whose clause is empty, so moving the return inside it leaves a path with no return at all.
            boolean typedCatch = byHandler.values().stream().flatMap(List::stream)
                    .anyMatch(e -> e.type() != null);
            if (typedCatch && follow >= 0 && follow < cfg.blocks.size() && !body.stack.isEmpty()) {
                Blk fb = cfg.blocks.get(follow);
                if (fb.instrs.size() == 1 && fb.isReturnOrThrow()) {
                    tryBody.addAll(region(follow, stop));
                    follow = -1;
                }
            }

            NodeList<CatchClause> clauses = new NodeList<>();
            for (var en : byHandler.entrySet()) {
                int hb = en.getKey();
                Blk h = cfg.blocks.get(hb);
                Instruction first = (Instruction) cfg.seq.get(h.instrs.get(0));
                String varName = "e"; int skip = 0;
                int mintedSlot = -1;
                if (first instanceof StoreInstruction si) {
                    body.pos = h.instrs.get(0);
                    // A generated handler (try-with-resources, record-pattern match) has no
                    // LocalVariableTable entry for its exception temp, so it gets a method-unique minted
                    // name instead of one per slot.
                    boolean unnamed = body.locals != null && body.locals.at(si.slot(), body.pos) == null;
                    varName = unnamed ? body.freshName(si.slot()) : body.localName(si.slot());
                    if (unnamed) mintedSlot = si.slot();
                    body.declared.add(body.varKey(si.slot())); skip = 1;
                } else if (first instanceof StackInstruction sk && sk.opcode() == Opcode.POP) { skip = 1; }
                Type catchType = catchUnion(en.getValue());
                skipLead.put(hb, skip);
                Set<Integer> catchStop = new HashSet<>(stop);
                if (follow >= 0) catchStop.add(follow);
                // The clause's own CRT extent, where available, is authoritative: `follow` is where the try
                // and its handlers reconverge, which can land INSIDE a clause whose body contains a
                // conditional (an `&&` merges before the clause ends), truncating it.
                Integer crtEnd = cfg.catchEnd.get(hb);
                if (crtEnd != null) { catchStop.remove(follow); catchStop.add(crtEnd); }
                List<Statement> catchBody = region(hb, catchStop);
                // The exception temp's name applies only inside its clause. Its slot is commonly reused
                // afterwards -- a record-pattern match reuses the very slot holding the matched value -- and
                // leaving the alias in place renamed that value to the exception.
                if (mintedSlot >= 0) body.slotAlias.remove(mintedSlot);
                clauses.add(new CatchClause(new Parameter(catchType, varName), new BlockStmt(nl(catchBody))));
            }
            // Every normal exit of the protected region carries an inlined copy of the finally body:
            // the one after the try was consumed above, but a catch ARM's exit (catch+finally) ends with
            // its own copy, structured into the arm's statements. Strip those tails so the single
            // finally clause is the only occurrence -- leaving them would run the finally twice per arm.
            // The arms may sit one level in (an outer catch-all wrapping an inner typed level), so look
            // through a directly-nested TryStmt too.
            if (finallyBody != null && !finallyBody.isEmpty()) {
                // NOT stripped from the try body itself: javac inlines copies only at EXITS of the
                // protected region (after the try, at arm ends, before stashed returns) -- a try body
                // whose real last statement equals the finally body must keep it (finallyOnNormal).
                for (CatchClause c : clauses) stripFinallyTail(c.getBody().getStatements(), finallyBody);
                if (!tryBody.isEmpty() && tryBody.get(tryBody.size() - 1) instanceof TryStmt inner) {
                    for (CatchClause c : inner.getCatchClauses()) {
                        stripFinallyTail(c.getBody().getStatements(), finallyBody);
                    }
                }
            }
            TryStmt t = new TryStmt(new BlockStmt(nl(tryBody)), clauses,
                    finallyBody != null ? new BlockStmt(nl(finallyBody)) : null);
            out.add(atBlk(t, startBlk));
            return follow;
        }

        /** Remove {@code f}'s statements from the tail of {@code stmts} when they match 1:1 (by text). */
        private static void stripFinallyTail(List<Statement> stmts, List<Statement> f) {
            int n = stmts.size(), k = f.size();
            if (k == 0 || n < k) return;
            for (int i = 0; i < k; i++) {
                if (!stmts.get(n - k + i).toString().equals(f.get(i).toString())) return;
            }
            for (int i = 0; i < k; i++) stmts.remove(stmts.size() - 1);
        }



        // --- synchronized ---
        // Pattern: <lock> dup astore tmp monitorenter <body> aload tmp monitorexit goto follow,
        // guarded by a catch-all handler (astore; aload tmp; monitorexit; athrow). monitorenter/exit
        // are already no-ops in translate(); here we recover the synchronized wrapper and lock object.
        int emitSync(int monBlk, Set<Integer> stop, List<Statement> out) {
            emittedSync.add(monBlk);
            Blk mb = cfg.blocks.get(monBlk);
            int n = mb.instrs.size();
            // prologue tail is dup; astore tmp; monitorenter -> translate everything before it, keep the lock
            int keep = (n >= 3 && Body.isDup(cfg.seq.get(mb.instrs.get(n - 3)))) ? n - 3 : n - 1;
            int mark = body.stmts.size();
            List<CodeElement> ins = new ArrayList<>(); List<Integer> idxs = new ArrayList<>();
            for (int i = 0; i < keep; i++) { ins.add(cfg.seq.get(mb.instrs.get(i))); idxs.add(mb.instrs.get(i)); }
            body.translateSpan(ins, idxs, 0, ins.size());
            drainInto(out, mark);
            Expression lock = body.stack.isEmpty() ? new NameExpr("?") : body.stack.pop();

            int bodyStart = mb.fall;
            // the exception entry protecting the body gives the handler + protected-range end
            Exc guard = null;
            for (Exc e : cfg.excs) if (e.startBlk() == bodyStart && e.type() == null) { guard = e; break; }
            int follow, handlerBlk = guard != null ? guard.handlerBlk() : -1;
            if (guard != null) {
                int endId = guard.endBlkId();
                follow = endId < cfg.blocks.size() ? chainExit(endId) : -1;
                markChainVisited(endId, follow);              // the normal-exit goto block
                markHandlerVisited(handlerBlk);               // astore; aload tmp; monitorexit; athrow
                emittedTries.add(bodyStart); emittedTries.add(handlerBlk);
            } else {
                follow = -1;
            }
            Set<Integer> bodyStop = new HashSet<>(stop);
            if (handlerBlk >= 0) bodyStop.add(handlerBlk);
            if (follow >= 0) bodyStop.add(follow);
            List<Statement> bodyStmts = region(bodyStart, bodyStop);
            out.add(atBlk(new SynchronizedStmt(lock, new BlockStmt(nl(bodyStmts))), monBlk));
            return follow;
        }
        void markHandlerVisited(int hb) {
            Set<Integer> seen = new HashSet<>();
            Deque<Integer> q = new ArrayDeque<>(); q.add(hb);
            while (!q.isEmpty()) {
                int c = q.poll();
                if (c < 0 || !seen.add(c)) continue;
                visited[c] = true;
                Blk b = cfg.blocks.get(c);
                if (b.last != null && b.last.opcode() == Opcode.ATHROW) continue;
                for (int s : b.succ) q.add(s);
            }
        }

        /**
         * A recognized finally handler: {@code astore v; <F>; aload v; athrow} where {@code <F>} may span
         * several LAYOUT-CONTIGUOUS blocks (a finally body with a call or branch compiles to a diamond).
         * {@code hEnd} is the block whose tail is the {@code aload v; athrow} rethrow; {@code len} is the
         * flattened instruction count of {@code <F>} (astore and rethrow excluded).
         */
        record FinallyShape(int hb, int hEnd, int len, int slot) { }

        /** The handler's finally shape, or null when {@code hb} does not have it. */
        FinallyShape finallyShape(int hb) {
            Blk h = cfg.blocks.get(hb);
            if (h.instrs.isEmpty()) return null;
            if (!(cfg.seq.get(h.instrs.get(0)) instanceof StoreInstruction si)) return null;
            int slot = si.slot();
            int count = -1;   // excludes the leading astore
            for (int b = hb; b < cfg.blocks.size(); b++) {
                Blk blk = cfg.blocks.get(b);
                int n = blk.instrs.size();
                if (n >= 2
                        && ((Instruction) cfg.seq.get(blk.instrs.get(n - 1))).opcode() == Opcode.ATHROW
                        && cfg.seq.get(blk.instrs.get(n - 2)) instanceof LoadInstruction ld
                        && ld.slot() == slot) {
                    return new FinallyShape(hb, b, count + n - 2, slot);
                }
                // A block that leaves the method (return/other athrow) before the rethrow breaks the shape.
                if (blk.succ.isEmpty()) return null;
                count += n;
            }
            return null;
        }

        /** The flattened finally-body opcodes of a handler (astore and trailing aload;athrow excluded). */
        private List<Opcode> finallyOpcodes(FinallyShape fs) {
            List<Opcode> ops = new ArrayList<>();
            for (int b = fs.hb(); b <= fs.hEnd(); b++) {
                Blk blk = cfg.blocks.get(b);
                int from = b == fs.hb() ? 1 : 0;
                int to = b == fs.hEnd() ? blk.instrs.size() - 2 : blk.instrs.size();
                for (int i = from; i < to; i++) ops.add(((Instruction) cfg.seq.get(blk.instrs.get(i))).opcode());
            }
            return ops;
        }

        /**
         * Consume the inlined normal-path copy of a finally body starting at block {@code c0}: verify the
         * opcode sequence matches the handler's, mark fully-consumed blocks visited, and skip the consumed
         * prefix of a partially-consumed block. Returns the follow block (where the normal path resumes),
         * or {@link Integer#MIN_VALUE} when the copy does not match (caller falls back to a plain catch).
         */
        int consumeFinallyCopy(FinallyShape fs, int c0) {
            List<Opcode> want = finallyOpcodes(fs);
            if (want.isEmpty() || c0 >= cfg.blocks.size()) return Integer.MIN_VALUE;
            int at = 0;
            for (int b = c0; b < cfg.blocks.size() && at < want.size(); b++) {
                Blk blk = cfg.blocks.get(b);
                int i = 0;
                for (; i < blk.instrs.size() && at < want.size(); i++, at++) {
                    if (((Instruction) cfg.seq.get(blk.instrs.get(i))).opcode() != want.get(at)) {
                        return Integer.MIN_VALUE;
                    }
                }
                if (at < want.size()) {
                    visited[b] = true;   // fully consumed
                    continue;
                }
                if (i < blk.instrs.size()) {
                    skipLead.put(b, i); // partially consumed: the remainder is real follow-on code
                    return b;
                }
                visited[b] = true;
                return blk.gotoTarget >= 0 ? blk.gotoTarget : blk.fall;
            }
            return Integer.MIN_VALUE;
        }

        /**
         * Structure the finally body from its handler: the leading astore and the trailing
         * {@code aload; athrow} rethrow are hidden via skipLead/skipTail, and the blocks in between are
         * structured as an ordinary region (a finally body with branches recovers its diamonds normally).
         */
        List<Statement> extractFinally(FinallyShape fs) {
            skipLead.merge(fs.hb(), 1, Integer::sum);
            skipTail.put(fs.hEnd(), 2);
            return region(fs.hb(), Set.of());
        }
        /** Follow a straight-line chain from {@code b}, returning where it exits (goto target / fall). */
        int chainExit(int b) {
            Set<Integer> seen = new HashSet<>();
            int c = b;
            while (c >= 0 && c < cfg.blocks.size() && seen.add(c)) {
                Blk blk = cfg.blocks.get(c);
                if (blk.gotoTarget >= 0) return blk.gotoTarget;
                if (blk.succ.size() == 1 && blk.fall >= 0) { c = blk.fall; continue; }
                return blk.fall;   // return/throw (-1) or single fall
            }
            return -1;
        }
        void markChainVisited(int from, int to) {
            Set<Integer> seen = new HashSet<>();
            int c = from;
            while (c >= 0 && c != to && c < cfg.blocks.size() && seen.add(c)) {
                visited[c] = true;
                Blk b = cfg.blocks.get(c);
                c = b.gotoTarget >= 0 ? b.gotoTarget : b.fall;
            }
        }

        /** Union type for a multi-catch handler (A | B), or the single catch type (Throwable for catch-all). */
        Type catchUnion(List<Exc> group) {
            List<Type> types = new ArrayList<>();
            for (Exc e : group) types.add(e.type() == null ? StaticJavaParser.parseType("Throwable") : type(e.type()));
            if (types.size() == 1) return types.get(0);
            return new com.github.javaparser.ast.type.UnionType(
                    new NodeList<>(types.stream().map(t -> (ReferenceType) t).toList()));
        }

        /** The block where a try's normal exit and its catches reconverge (resolving trivial goto bridges). */
        int tryFollow(int startBlk, int endId, Set<Integer> handlerBlks) {
            int follow = -1;
            for (int id = startBlk; id < endId && id < cfg.blocks.size(); id++)
                for (int su : cfg.blocks.get(id).succ)
                    if ((su < startBlk || su >= endId) && !handlerBlks.contains(su) && (follow < 0 || su < follow))
                        follow = su;
            // A try body that ALWAYS throws has no successor outside the protected range, so nothing above
            // matches. Without a follow the catch body runs on and swallows everything after the statement --
            // which is how two sibling try/catch blocks ended up nested, their `catch (X e)` params colliding.
            // The handlers still reconverge on the code that follows, so take that instead.
            // Only for a handler written in source, identified by its parameter having a LocalVariableTable
            // entry. A COMPILER-GENERATED handler (try-with-resources close/suppress) has none, and its body
            // holds a nested try whose block would be mistaken for the follow, truncating the handler.
            if (follow < 0)
                for (int h : handlerBlks) {
                    if (!hasNamedParam(h)) continue;
                    int after = dom.ipdom[h];
                    if (after >= 0 && !handlerBlks.contains(after) && (follow < 0 || after < follow))
                        follow = after;
                }
            java.util.Set<Integer> seen = new HashSet<>();
            while (follow >= 0 && seen.add(follow) && isPureGoto(cfg.blocks.get(follow)))
                follow = cfg.blocks.get(follow).gotoTarget;
            return follow;
        }
        boolean isPureGoto(Blk b) {
            return b.instrs.size() == 1 && b.last instanceof BranchInstruction br && br.opcode() == Opcode.GOTO;
        }

        /** True if this handler's exception parameter is in the LocalVariableTable, i.e. it was written in source. */
        boolean hasNamedParam(int handlerBlk) {
            Blk h = cfg.blocks.get(handlerBlk);
            if (h.instrs.isEmpty() || body.locals == null) return false;
            int at = h.instrs.get(0);
            return cfg.seq.get(at) instanceof StoreInstruction si && body.locals.at(si.slot(), at) != null;
        }

        // --- helpers ---

        /** Translate a straight-line block's instructions into statements (dropping the structural GOTO). */
        void translateBlock(Blk b, List<Statement> out) {
            int mark = body.stmts.size();
            List<CodeElement> ins = elems(b);
            body.translateInstrs(ins, elemIdxs(b));   // translateSpan skips the structural GOTO and sets pos per instr
            drainInto(out, mark);
        }

        /** All instructions of a block, as CodeElements (for peephole-aware translation), honoring skipLead/skipTail. */
        List<CodeElement> elems(Blk b) {
            List<CodeElement> l = new ArrayList<>();
            int end = b.instrs.size() - skipTail.getOrDefault(b.id, 0);
            for (int i = skipLead.getOrDefault(b.id, 0); i < end; i++) l.add(cfg.seq.get(b.instrs.get(i)));
            return l;
        }
        /** The parallel real seq-indices for {@link #elems}, so scoped locals resolve per instruction. */
        List<Integer> elemIdxs(Blk b) {
            List<Integer> l = new ArrayList<>();
            int end = b.instrs.size() - skipTail.getOrDefault(b.id, 0);
            for (int i = skipLead.getOrDefault(b.id, 0); i < end; i++) l.add(b.instrs.get(i));
            return l;
        }

        /** Translate a conditional block's operands and return its fall-through (not-branch) condition. */
        Expression conditionOf(Blk b, List<Statement> preOut) {
            int mark = body.stmts.size();
            List<CodeElement> ins = elems(b);
            body.translateSpan(ins, elemIdxs(b), 0, ins.size() - 1);   // all but the branch, peephole-aware
            drainInto(preOut, mark);     // any side-effect statements precede the test
            return body.materializeComparison(b.last.opcode());
        }

        /** javac boolean-materialization diamond: cond ? push 1 : push 0, reconverging at ipdom. */
        Integer booleanDiamond(int condBlk) {
            Blk c = cfg.blocks.get(condBlk);
            if (!c.isConditional()) return null;
            int merge = dom.ipdom[condBlk];
            if (merge < 0) return null;
            Blk t = cfg.blocks.get(c.fall), f = cfg.blocks.get(c.branch);
            boolean tTrue = ops(t).equals(List.of(Opcode.ICONST_1, Opcode.GOTO)) && only(t.succ, merge);
            boolean fFalse = ops(f).equals(List.of(Opcode.ICONST_0)) && only(f.succ, merge);
            return (tTrue && fFalse) ? merge : null;
        }
        void emitDiamondValue(Blk c, List<Statement> out) {
            visited[c.fall] = true; visited[c.branch] = true;   // the two iconst value blocks are folded in
            Expression v = conditionOf(c, out);  // drains leading statements; peephole-aware operand translation
            body.bools.add(v);
            body.stack.push(v);
        }

        /**
         * General ternary value-diamond (cond ? a : b): a 2-way conditional whose two arms each
         * produce exactly one value (no statements) and reconverge at ipdom. Generalizes the
         * boolean diamond (which is the a=true,b=false special case handled above).
         */
        Integer valueDiamond(int condBlk) {
            Blk c = cfg.blocks.get(condBlk);
            if (!c.isConditional()) return null;
            int merge = dom.ipdom[condBlk];
            if (merge < 0 || merge == condBlk || c.fall == merge || c.branch == merge) return null;
            if (armIsValue(c.fall, merge, new HashSet<>()) && armIsValue(c.branch, merge, new HashSet<>()))
                return merge;
            return null;
        }

        /** Static structural check: region [entry, merge) pushes exactly one value and emits no statements. */
        boolean armIsValue(int entry, int merge, Set<Integer> seen) {
            int cur = entry;
            while (cur != merge) {
                if (cur < 0 || !seen.add(cur)) return false;
                Blk b = cfg.blocks.get(cur);
                if (b.isSwitch() || blockHasStatement(b)) return false;   // arms must be side-effect free
                if (b.isConditional()) {
                    Integer bd = booleanDiamond(cur);
                    if (bd != null) { cur = bd; continue; }
                    int m = dom.ipdom[cur];
                    if (m < 0 || m == cur) return false;
                    if (!armIsValue(b.fall, m, new HashSet<>()) || !armIsValue(b.branch, m, new HashSet<>())) return false;
                    cur = m;
                } else {
                    cur = b.gotoTarget >= 0 ? b.gotoTarget : b.fall;
                }
            }
            return true;
        }

        boolean blockHasStatement(Blk b) {
            for (int i = skipLead.getOrDefault(b.id, 0); i < b.instrs.size(); i++) {
                Instruction in = (Instruction) cfg.seq.get(b.instrs.get(i));
                // [bytecode] An iinc that pairs with a load of the same slot in this block is the
                // postfix/prefix operator of an expression (`y++ > 5` in a condition arm), which
                // Body.translate absorbs -- not a statement. Treating it as one collapsed
                // `b = y > 0 && y++ > 5` to its false arm, silently dropping the increment.
                if (in instanceof IncrementInstruction inc) {
                    if (incAbsorbableInBlock(b, i, inc)) continue;
                    return true;
                }
                if (in instanceof StoreInstruction || in instanceof ArrayStoreInstruction
                        || in instanceof ReturnInstruction
                        || in.opcode() == Opcode.ATHROW) return true;
                if (in instanceof InvokeInstruction inv && inv.typeSymbol().returnType().descriptorString().equals("V")) return true;
                if (in instanceof FieldInstruction fi && (fi.opcode() == Opcode.PUTFIELD || fi.opcode() == Opcode.PUTSTATIC)) return true;
            }
            return false;
        }

        /** [bytecode] True when an iinc at index {@code at} has a load of the same slot in this block
         *  (before it: postfix; after it: prefix), i.e. the increment is part of an expression. */
        private boolean incAbsorbableInBlock(Blk b, int at, IncrementInstruction inc) {
            int c = inc.constant();
            if (c != 1 && c != -1) return false;
            for (int j = skipLead.getOrDefault(b.id, 0); j < b.instrs.size(); j++) {
                if (j == at) continue;
                if (cfg.seq.get(b.instrs.get(j)) instanceof LoadInstruction li && li.slot() == inc.slot()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Recover the binding of {@code x instanceof T t && <test on t>} used as a VALUE.
         *
         * <p>javac tests the type, then binds {@code t} at the head of the block carrying the NEXT condition.
         * That store makes the arm look impure, so the value diamond declines and the expression collapses to
         * its false arm -- {@code boolean a = matching instanceof String s && s.length() == 5} came back as
         * {@code a = false}, with the binding stranded in an if statement that computed nothing.
         *
         * <p>Hiding the bind prefix lets the ordinary diamond recovery apply, and the slot is recorded so the
         * condition can carry it as a type pattern once it is built. Rolled back if the diamond still
         * declines, since a skipped prefix that nobody consumes would drop three instructions.
         */
        void foldChainPatternBind(int condBlk) {
            Blk c = cfg.blocks.get(condBlk);
            if (!c.isConditional() || c.last.opcode() != Opcode.IFEQ) return;
            int n = c.instrs.size();
            if (n < 2 || !(insAt(c, n - 2) instanceof TypeCheckInstruction ti)
                    || ti.opcode() != Opcode.INSTANCEOF) return;
            int fb = c.fall;
            if (fb < 0 || fb >= cfg.blocks.size() || skipLead.containsKey(fb)) return;
            Blk b = cfg.blocks.get(fb);
            if (b.instrs.size() < 4) return;                       // the bind, plus the condition it precedes
            if (!(insAt(b, 0) instanceof LoadInstruction)) return;
            if (!(insAt(b, 1) instanceof TypeCheckInstruction cc) || cc.opcode() != Opcode.CHECKCAST) return;
            if (!(insAt(b, 2) instanceof StoreInstruction st)) return;

            skipLead.put(fb, 3);
            patternBind.put(condBlk, new int[]{ st.slot(), b.instrs.get(2) });
            if (valueDiamond(condBlk) == null) { skipLead.remove(fb); patternBind.remove(condBlk); }
        }

        void emitValueDiamond(int condBlk, int merge, List<Statement> out) {
            Blk c = cfg.blocks.get(condBlk);
            Expression cond = conditionOf(c, out);          // drains leading statements (e.g. int x = 5;)
            int[] pb = patternBind.get(condBlk);
            if (pb != null && cond instanceof InstanceOfExpr io && io.getPattern().isEmpty()) {
                body.pos = pb[1];
                io.setPattern(new TypePatternExpr(new NodeList<>(), body.localType(pb[0]),
                        new SimpleName(body.localName(pb[0]))));
                body.declared.add(body.varKey(pb[0]));
            }
            int mark = body.stmts.size();
            Expression thenVal = evalArm(c.fall, merge);
            Expression elseVal = evalArm(c.branch, merge);
            if (body.stmts.size() != mark) throw new StructException("value arm produced statements");
            body.stack.push(buildTernary(cond, thenVal, elseVal));
        }

        /** Build `cond ? a : b`, simplifying boolean forms to &&/|| ( a?b:false => a&&b ; a?true:b => a||b ). */
        Expression buildTernary(Expression cond, Expression thenVal, Expression elseVal) {
            boolean boolish = isBoolish(thenVal) || isBoolish(elseVal);
            if (boolish) { thenVal = coerce(thenVal, "Z"); elseVal = coerce(elseVal, "Z"); }
            Expression r;
            if (boolish && isFalseLit(elseVal))        // a ? b : false  =>  a && b
                r = binexpr(cond, BinaryExpr.Operator.AND, thenVal);
            else if (boolish && isTrueLit(thenVal))     // a ? true : b   =>  a || b
                r = binexpr(cond, BinaryExpr.Operator.OR, elseVal);
            else if (boolish && isTrueLit(elseVal))     // a ? b : true   =>  !a || b
                r = binexpr(negate(cond), BinaryExpr.Operator.OR, thenVal);
            else if (boolish && isFalseLit(thenVal))    // a ? false : b  =>  !a && b
                r = binexpr(negate(cond), BinaryExpr.Operator.AND, elseVal);
            else
                r = new ConditionalExpr(par(cond), par(thenVal), par(elseVal));
            if (boolish) body.bools.add(r);
            return r;
        }
        static boolean isTrueLit(Expression e)  { return e instanceof BooleanLiteralExpr b && b.getValue(); }
        static boolean isFalseLit(Expression e) { return e instanceof BooleanLiteralExpr b && !b.getValue(); }

        /** Evaluate a pure value arm [entry, merge) as a single expression (recursing into nested ternaries). */
        Expression evalArm(int entry, int merge) {
            int cur = entry;
            while (cur != merge) {
                visited[cur] = true;
                Blk b = cfg.blocks.get(cur);
                body.pos = b.instrs.isEmpty() ? body.pos : b.instrs.get(0);
                if (b.isConditional()) {
                    Integer bd = booleanDiamond(cur);
                    if (bd != null) {
                        visited[b.fall] = true; visited[b.branch] = true;
                        List<CodeElement> ins = elems(b);
                        body.translateSpan(ins, elemIdxs(b), 0, ins.size() - 1);
                        Expression v = body.materializeComparison(b.last.opcode());
                        body.bools.add(v); body.stack.push(v); cur = bd;
                    } else {
                        int m = dom.ipdom[cur];
                        body.stack.push(evalTernaryValue(cur, m)); cur = m;
                    }
                } else {
                    List<CodeElement> ins = elems(b);
                    body.translateSpan(ins, elemIdxs(b), 0, ins.size());   // gotos skipped internally
                    cur = b.gotoTarget >= 0 ? b.gotoTarget : b.fall;
                }
            }
            return body.stack.pop();
        }

        Expression evalTernaryValue(int condBlk, int merge) {
            Blk c = cfg.blocks.get(condBlk);
            visited[condBlk] = true;
            List<CodeElement> ins = elems(c);
            body.translateSpan(ins, elemIdxs(c), 0, ins.size() - 1);
            Expression cond = body.materializeComparison(c.last.opcode());
            Expression thenVal = evalArm(c.fall, merge);
            Expression elseVal = evalArm(c.branch, merge);
            return buildTernary(cond, thenVal, elseVal);
        }

        boolean isBoolish(Expression e) { return body.bools.contains(e) || body.isBooleanish(e); }

        int pickBodyEntry(Blk h, Set<Integer> bodyBlocks) {
            for (int s : h.succ) if (bodyBlocks.contains(s)) return s;
            return h.succ.isEmpty() ? -1 : h.succ.get(0);
        }
        int firstExit(Set<Integer> bodyBlocks) {
            for (int bId : bodyBlocks)
                for (int s : cfg.blocks.get(bId).succ)
                    if (!bodyBlocks.contains(s)) return s;
            return -1;
        }

        List<Opcode> ops(Blk b) {
            List<Opcode> o = new ArrayList<>();
            for (int i : b.instrs) o.add(((Instruction) cfg.seq.get(i)).opcode());
            return o;
        }
        static boolean only(List<Integer> succ, int x) { return succ.size() == 1 && succ.get(0) == x; }
        void drainInto(List<Statement> out, int mark) {
            for (int i = mark; i < body.stmts.size(); i++) out.add(body.stmts.get(i));
            while (body.stmts.size() > mark) body.stmts.remove(body.stmts.size() - 1);
        }
        static NodeList<Statement> nl(List<Statement> ss) { return new NodeList<>(ss); }
        static Set<Integer> plus(Set<Integer> s, int x) {
            if (x < 0) return s;
            Set<Integer> r = new HashSet<>(s); r.add(x); return r;
        }

        /**
         * Tag a structural statement (if / while / do / switch / try / synchronized) with the source span of
         * the block that controls it. The span is taken from the block's TERMINATOR (the branch or switch
         * itself), not its first instruction: a block also holds the straight-line statements that precede
         * the test, so the first instruction would report the preceding statement's line instead.
         */
        <T extends Statement> T atBlk(T s, int blkId) {
            int seqIdx = terminatorOf(blkId);
            if (seqIdx >= 0) body.attachSpan(s, seqIdx);
            return s;
        }

        /** Seq-index of a block's terminator (its last instruction), or -1. */
        int terminatorOf(int blkId) {
            if (blkId < 0 || blkId >= cfg.blocks.size()) return -1;
            Blk b = cfg.blocks.get(blkId);
            return b.instrs.isEmpty() ? -1 : b.instrs.get(b.instrs.size() - 1);
        }
    }

    static Expression negate(Expression e) {
        if (e instanceof EnclosedExpr en) return negate(en.getInner());
        if (e instanceof UnaryExpr u && u.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) return u.getExpression();
        if (e instanceof BinaryExpr b) {
            // A floating-point relational test cannot be negated by flipping the operator: NaN makes all
            // four of < <= > >= false, so `!(x < 0.0)` holds for NaN while `x >= 0.0` does not. Equality
            // still flips safely, since `!(a == b)` and `a != b` agree even on NaN.
            // getData throws when the key is absent, so the presence check is required, not defensive
            boolean unordered = b.containsData(UNORDERED) && Boolean.TRUE.equals(b.getData(UNORDERED));
            BinaryExpr.Operator f = switch (b.getOperator()) {
                case EQUALS -> BinaryExpr.Operator.NOT_EQUALS;
                case NOT_EQUALS -> BinaryExpr.Operator.EQUALS;
                case LESS -> unordered ? null : BinaryExpr.Operator.GREATER_EQUALS;
                case GREATER_EQUALS -> unordered ? null : BinaryExpr.Operator.LESS;
                case GREATER -> unordered ? null : BinaryExpr.Operator.LESS_EQUALS;
                case LESS_EQUALS -> unordered ? null : BinaryExpr.Operator.GREATER;
                default -> null;
            };
            if (f != null) return new BinaryExpr(b.getLeft(), b.getRight(), f);
        }
        return new UnaryExpr(par(e), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
    }

    static void printStruct(Path classFile, String method) throws Exception {
        ClassModel cm = ClassFile.of().parse(classFile);
        MethodModel m = cm.methods().stream()
                .filter(x -> x.methodName().stringValue().equals(method)).findFirst().orElseThrow();
        Cfg cfg = buildCfg(cm, m);
        Dom dom = analyze(cfg);

        Map<Integer,String> slotName = new HashMap<>();
        Map<Integer,String> slotDesc = new HashMap<>();
        List<LocalVariable> lvs = new ArrayList<>();
        List<LocalVariableType> lvts = new ArrayList<>();
        CodeAttribute code = (CodeAttribute) m.findAttribute(Attributes.code()).orElseThrow();
        for (CodeElement e : code) {
            if (e instanceof LocalVariable lv) { slotName.put(lv.slot(), lv.name().stringValue());
                slotDesc.put(lv.slot(), lv.type().stringValue()); lvs.add(lv); }
            else if (e instanceof LocalVariableType lvt) lvts.add(lvt);
        }
        Locals locals = Locals.from(lvs, lvts, cfg.labelPos);
        Set<String> declared = new HashSet<>();
        boolean isStatic = m.flags().has(AccessFlag.STATIC);
        MethodTypeDesc mt = m.methodTypeSymbol();
        int slot = isStatic ? 0 : 1;
        for (int i = 0; i < mt.parameterCount(); i++) {
            Locals.Scope ps = locals.at(slot, 0);
            declared.add(ps != null ? (slot + "@" + ps.start()) : ("v" + slot));
            String d = mt.parameterType(i).descriptorString();
            slot += (d.equals("J") || d.equals("D")) ? 2 : 1;
        }
        Body body = new Body(slotName, slotDesc, declared);
        body.locals = locals; body.cm = cm;
        body.crt = crtRanges(code); body.seqToBci = bciTable(cfg.seq);
        BlockStmt block = new Structurer(cfg, dom, body).structure();
        annotateSpans(block);
        MethodDeclaration md = new MethodDeclaration();
        md.setName(method);
        md.setType(type(mt.returnType()));
        md.setBody(block);
        System.out.println(render(md.toString()));
    }

    /** Reconstruct a compilation unit, then give it the imports its shortened type names require. */
    static CompilationUnit build(ClassModel cm) { return build(cm, Map.of()); }

    /**
     * Reconstruct a compilation unit. {@code pool} maps internal names to the other classfiles of the same
     * compilation, so nested classes can be re-nested into this declaration instead of being emitted as
     * separate top-level files (see {@link #attachNested}).
     */
    static CompilationUnit build(ClassModel cm, Map<String,ClassModel> pool) {
        CompilationUnit cu = new CompilationUnit();
        String internal = cm.thisClass().asInternalName();
        int slash = internal.lastIndexOf('/');
        String pkg = slash > 0 ? internal.substring(0, slash).replace('/', '.') : "";
        if (slash > 0) cu.setPackageDeclaration(pkg);
        // type references are collected per class, as they are shortened
        imports.reset(pkg, internal.replace('/', '.').replace('$', '.'));
        cu.addType(buildDecl(cm, pool, null));
        imports.applyTo(cu);
        return cu;
    }

    /**
     * Build the declaration for one class: record or class/interface, its members, and any nested classes
     * pulled from {@code pool}. {@code nested} is this class's {@code InnerClasses} entry when it is being
     * built as a member of its enclosing class, which is where the real source name and modifiers live.
     */
    static TypeDeclaration<?> buildDecl(ClassModel cm, Map<String,ClassModel> pool, InnerClassInfo nested) {
        String internal = cm.thisClass().asInternalName();
        String name = nested != null ? nestedName(nested, internal) : simple(internal);

        RecordAttribute rec = cm.findAttribute(Attributes.record()).orElse(null);
        if (rec != null) return buildRecord(cm, rec, name, nested, pool);
        if (isEnumClass(cm)) return buildEnum(cm, name, nested, pool);
        if (cm.flags().has(AccessFlag.ANNOTATION)) return buildAnnotationDecl(cm, name, nested);

        ClassOrInterfaceDeclaration cls = new ClassOrInterfaceDeclaration();
        cls.setName(name);
        cls.setModifiers(nestedModifiers(cm, nested, false));
        for (AnnotationExpr a : annotationsOn(cm)) cls.addAnnotation(a);
        // class-level generic type parameters (e.g. class Box<T>)
        cm.findAttribute(Attributes.signature()).ifPresent(sa -> {
            try {
                ClassSignature cs = ClassSignature.parseFrom(sa.signature().stringValue());
                if (!cs.typeParameters().isEmpty()) cls.setTypeParameters(typeParams(cs.typeParameters()));
            } catch (RuntimeException ignore) { }
        });
        applySupertypes(cls, cm);          // extends / implements (+ interface-ness)
        // Fields with no source form: $assertionsDisabled backs the `assert` desugaring (recovered as an
        // assert statement), this$0 holds an inner class's enclosing instance (recovered as Outer.this), and
        // val$x holds a captured local (referenced by its own name).
        // this$0 holds an inner class's enclosing instance and is recovered as Outer.this instead. A val$x
        // capture field is KEPT: a local class hoisted to member position no longer has the captured local in
        // scope, so the field (and the constructor parameter feeding it) is what makes the reference resolve.
        emitFields(cls, cm, Set.of("$assertionsDisabled", "this$0"));
        boolean enumClass = isEnumClass(cm);
        for (MethodModel m : cm.methods()) {
            String n = m.methodName().stringValue();
            // <clinit> is not a source member, but it holds the field initializers and static blocks that
            // are. An enum's is skipped: it builds the constants and $VALUES, which the declaration implies.
            if (n.equals("<clinit>")) { if (!enumClass) emitStaticInit(cls, cm, m); continue; }
            if (isBridge(m)) continue;                           // erasure shim, not a source member
            // An enum's constructor carries the synthetic (name, ordinal) prologue the compiler adds and
            // cannot be written in source; enums are not reconstructed yet, so it is skipped.
            if (n.equals("<init>") && enumClass) continue;
            // A non-static inner class's constructor takes the enclosing instance as a hidden first
            // parameter, which cannot be written in source (the caller writes `outer.new Inner()`).
            if (n.equals("<init>")) { decompileInto(cls, m, cm, isInnerCtor(cm, m, nested) ? 1 : 0, false); continue; }
            decompileInto(cls, m, cm);
        }
        dropDefaultCtor(cls);
        attachNested(cls, cm, pool);
        return cls;
    }

    /**
     * Re-nest this class's nested classes as members of {@code decl}, reading them from {@code pool}.
     *
     * <p>Each nested class is its own classfile, so emitting one file per classfile turns {@code Outer$Inner}
     * into a top-level {@code class Inner}: references written {@code Outer.Inner} then cannot resolve, and a
     * nested name reused by several outers in one package collides. Rebuilding them as members fixes both.
     *
     * <p>Local and anonymous classes are declared inside a method body, not at member level. Their statement
     * position is not recovered, so they are hoisted to {@code static} members -- legal, resolvable, and
     * scoped no more widely than the file, but not where the source had them.
     */
    static void attachNested(TypeDeclaration<?> decl, ClassModel cm, Map<String,ClassModel> pool) {
        attachNested(decl, cm, pool, Set.of());
    }

    /** As above, but {@code exclude}d internal names are skipped (an enum constant's body subclass). */
    static void attachNested(TypeDeclaration<?> decl, ClassModel cm, Map<String,ClassModel> pool,
                             Set<String> exclude) {
        String self = cm.thisClass().asInternalName();
        for (InnerClassInfo ic : innerClassesOf(cm)) {
            String inner = ic.innerClass().asInternalName();
            if (inner.equals(self) || exclude.contains(inner)) continue;    // own entry, or consumed elsewhere
            if (!self.equals(directOuter(pool.get(inner), ic))) continue;   // not OUR nested class
            ClassModel nested = pool.get(inner);
            if (nested == null) continue;                            // classfile not in this compilation
            var member = buildDecl(nested, pool, ic);
            if (ic.innerName().isEmpty()) {
                member.setData(ANONYMOUS, true);                     // hoisted anonymous class
            }
            decl.addMember(member);
        }
    }

    /** The {@code InnerClasses} entries of a class (empty when absent). */
    static List<InnerClassInfo> innerClassesOf(ClassModel cm) {
        return cm.findAttribute(Attributes.innerClasses())
                 .map(InnerClassesAttribute::classes).orElse(List.of());
    }

    /**
     * The internal name of the class a nested class is declared directly inside, or null if it is top-level.
     * A member class records its enclosing class in its {@code InnerClasses} entry; a local or anonymous
     * class does not (it is not a member), so its {@code EnclosingMethod} is used instead.
     */
    static String directOuter(ClassModel nested, InnerClassInfo ic) {
        var outer = ic.outerClass();
        if (outer.isPresent()) return outer.get().asInternalName();
        if (nested == null) return null;
        return nested.findAttribute(Attributes.enclosingMethod())
                     .map(em -> em.enclosingClass().asInternalName()).orElse(null);
    }

    /**
     * Reconstruct an {@code @interface}. In bytecode it is an interface carrying ACC_ANNOTATION and an
     * explicit {@code extends java.lang.annotation.Annotation}; both of those are implied by the
     * {@code @interface} keyword and cannot be written in source. Its elements are abstract methods, whose
     * defaults live in a per-method {@code AnnotationDefault} attribute.
     */
    static AnnotationDeclaration buildAnnotationDecl(ClassModel cm, String name, InnerClassInfo nested) {
        AnnotationDeclaration ad = new AnnotationDeclaration();
        ad.setName(name);
        for (Modifier.Keyword k : nestedModifiers(cm, nested, true))
            if (k == Modifier.Keyword.PUBLIC || k == Modifier.Keyword.PRIVATE || k == Modifier.Keyword.PROTECTED)
                ad.addModifier(k);
        for (var a : annotationsOn(cm)) ad.addAnnotation(a);
        emitFields(ad, cm, Set.of());
        for (MethodModel m : cm.methods()) {
            String n = m.methodName().stringValue();
            // an annotation type can declare constants, whose non-constant initialisers live in <clinit>
            if (n.equals("<clinit>")) { emitStaticInit(ad, cm, m); continue; }
            if (n.equals("<init>") || isBridge(m)) continue;
            AnnotationMemberDeclaration el = new AnnotationMemberDeclaration();
            el.setName(n);
            el.setType(type(m.methodTypeSymbol().returnType()));
            m.findAttribute(Attributes.annotationDefault())
                    .ifPresent(def -> el.setDefaultValue(annotationValue(def.defaultValue())));
            ad.addMember(el);
        }
        return ad;
    }

    /** The annotations written on a declaration, from both the visible and invisible attributes. */
    static <T extends java.lang.classfile.AttributedElement> List<AnnotationExpr> annotationsOn(T el) {
        List<AnnotationExpr> out = new ArrayList<>();
        el.findAttribute(Attributes.runtimeVisibleAnnotations())
          .ifPresent(a -> { for (var an : a.annotations()) add(out, an); });
        // CLASS-retention annotations land in the "invisible" attribute: not kept for reflection, but still
        // recorded, so still recoverable. SOURCE retention is dropped by javac and is gone for good.
        el.findAttribute(Attributes.runtimeInvisibleAnnotations())
          .ifPresent(a -> { for (var an : a.annotations()) add(out, an); });
        return out;
    }

    private static void add(List<AnnotationExpr> out, java.lang.classfile.Annotation an) {
        AnnotationExpr e = toAnnotationExpr(an);
        if (e != null) out.add(e);
    }

    /** [bytecode] Type-use annotations (visible + invisible) recorded on an element. CLASS-retention
     *  type annotations (@Nat, @Unbounded, @Nullable) land in RuntimeInvisibleTypeAnnotations. */
    static <T extends java.lang.classfile.AttributedElement> List<java.lang.classfile.TypeAnnotation> typeAnnotationsOn(T el) {
        List<java.lang.classfile.TypeAnnotation> out = new ArrayList<>();
        el.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
          .ifPresent(a -> out.addAll(a.annotations()));
        el.findAttribute(Attributes.runtimeInvisibleTypeAnnotations())
          .ifPresent(a -> out.addAll(a.annotations()));
        return out;
    }

    /** [bytecode] Attach a recovered annotation to a type's annotation list ((@Nat int) etc.). */
    static void annotateType(Type t, java.lang.classfile.Annotation an) {
        AnnotationExpr e = toAnnotationExpr(an);
        if (e == null) return;
        NodeList<AnnotationExpr> list = new NodeList<>(t.getAnnotations());
        list.add(e);
        t.setAnnotations(list);
    }

    /** Render one annotation, choosing the marker, single-element or full form as the source would. */
    static AnnotationExpr toAnnotationExpr(java.lang.classfile.Annotation an) {
        try {
            String internal = innerName(ClassDesc.ofDescriptor(an.className().stringValue()));
            com.github.javaparser.ast.expr.Name nm =
                    new com.github.javaparser.ast.expr.Name(simpleRef(internal));
            var elems = an.elements();
            if (elems.isEmpty()) return new MarkerAnnotationExpr(nm);
            if (elems.size() == 1 && elems.get(0).name().stringValue().equals("value"))
                return new SingleMemberAnnotationExpr(nm, annotationValue(elems.get(0).value()));
            NodeList<MemberValuePair> pairs = new NodeList<>();
            for (var ae : elems)
                pairs.add(new MemberValuePair(ae.name().stringValue(), annotationValue(ae.value())));
            return new NormalAnnotationExpr(nm, pairs);
        } catch (RuntimeException e) { return null; }
    }

    /** Render an annotation element value. An enum constant is qualified, since only its type is recorded. */
    static Expression annotationValue(AnnotationValue v) {
        return switch (v) {
            case AnnotationValue.OfString s -> strLit(s.stringValue());
            case AnnotationValue.OfBoolean b -> new BooleanLiteralExpr(b.booleanValue());
            case AnnotationValue.OfByte b -> new CastExpr(StaticJavaParser.parseType("byte"),
                    new IntegerLiteralExpr(String.valueOf(b.byteValue())));
            case AnnotationValue.OfShort s -> new CastExpr(StaticJavaParser.parseType("short"),
                    new IntegerLiteralExpr(String.valueOf(s.shortValue())));
            case AnnotationValue.OfChar c -> new com.github.javaparser.ast.expr.CharLiteralExpr(
                    String.valueOf(c.charValue()));
            case AnnotationValue.OfInt i -> new IntegerLiteralExpr(String.valueOf(i.intValue()));
            case AnnotationValue.OfLong l -> new LongLiteralExpr(l.longValue() + "L");
            case AnnotationValue.OfFloat f -> floatLit(f.floatValue());
            case AnnotationValue.OfDouble d -> doubleLit(d.doubleValue());
            case AnnotationValue.OfClass c -> new ClassExpr(type(ClassDesc.ofDescriptor(c.className().stringValue())));
            case AnnotationValue.OfEnum e -> new FieldAccessExpr(
                    new NameExpr(simpleRef(innerName(ClassDesc.ofDescriptor(e.className().stringValue())))),
                    e.constantName().stringValue());
            case AnnotationValue.OfAnnotation a -> {
                AnnotationExpr inner = toAnnotationExpr(a.annotation());
                yield inner != null ? new NameExpr(inner.toString()) : new NameExpr("/*annotation*/");
            }
            case AnnotationValue.OfArray a -> {
                NodeList<Expression> vals = new NodeList<>();
                for (AnnotationValue x : a.values()) vals.add(annotationValue(x));
                yield new ArrayInitializerExpr(vals);
            }
        };
    }

    /** One recovered enum constant: its name, its declaration arguments, and its body's class if it has one. */
    record EnumConst(String name, List<Expression> args, String subclass) { }

    /**
     * Reconstruct an {@code enum}. The declaration is spread across several compiler-generated pieces:
     *
     * <ul>
     *   <li>the class itself is {@code final class X extends Enum<X>}, which is not writable in source;</li>
     *   <li>each constant is a {@code public static final X} field, assigned in {@code <clinit>} -- which is
     *       the ONLY place the constant's declaration arguments and its order survive;</li>
     *   <li>a constant with a body is a separate anonymous subclass, instantiated in place of the enum;</li>
     *   <li>{@code $VALUES}, {@code values()}, {@code valueOf(String)} and {@code $values()} are synthetic;</li>
     *   <li>the constructor gains a leading {@code (String name, int ordinal)} pair and a matching
     *       {@code super(...)} call, neither of which is written in source.</li>
     * </ul>
     */
    static EnumDeclaration buildEnum(ClassModel cm, String name, InnerClassInfo nested,
                                     Map<String,ClassModel> pool) {
        EnumDeclaration ed = new EnumDeclaration();
        ed.setName(name);
        // an enum is implicitly final/static; only its access modifier is writable
        for (Modifier.Keyword k : nestedModifiers(cm, nested, true))
            if (k == Modifier.Keyword.PUBLIC || k == Modifier.Keyword.PRIVATE || k == Modifier.Keyword.PROTECTED)
                ed.addModifier(k);
        List<ClassOrInterfaceType> ifaces = interfaceTypes(cm, classSignature(cm));
        if (!ifaces.isEmpty()) ed.setImplementedTypes(new NodeList<>(ifaces));

        List<EnumConst> consts = enumConstants(cm);
        Set<String> constNames = new HashSet<>();
        Set<String> bodyClasses = new HashSet<>();
        for (EnumConst c : consts) {
            constNames.add(c.name());
            EnumConstantDeclaration ec = new EnumConstantDeclaration(c.name());
            if (!c.args().isEmpty()) ec.setArguments(new NodeList<>(c.args()));
            // A constant with a body compiles to an anonymous subclass; its members ARE that body, so they
            // are moved here and the subclass is not emitted separately.
            if (c.subclass() != null) {
                bodyClasses.add(c.subclass());
                ClassModel sub = pool.get(c.subclass());
                if (sub != null) {
                    ClassOrInterfaceDeclaration holder = new ClassOrInterfaceDeclaration();
                    holder.setName("H");
                    for (MethodModel m : sub.methods())
                        if (!m.methodName().stringValue().equals("<init>")
                                && !m.methodName().stringValue().equals("<clinit>"))
                            decompileInto(holder, m, sub);
                    emitFields(holder, sub, Set.of());
                    if (!holder.getMembers().isEmpty()) ec.setClassBody(new NodeList<>(holder.getMembers()));
                }
            }
            ed.addEntry(ec);
        }

        Set<String> skipFields = new HashSet<>(constNames);
        skipFields.add("$VALUES");
        emitFields(ed, cm, skipFields);

        for (MethodModel m : cm.methods()) {
            String n = m.methodName().stringValue();
            if (n.equals("<clinit>") || n.equals("$values") || n.equals("values") || n.equals("valueOf")) continue;
            // the constructor's leading (name, ordinal) pair and its super(...) are compiler-added
            if (n.equals("<init>")) { decompileInto(ed, m, cm, 2, true); continue; }
            decompileInto(ed, m, cm);
        }
        dropDefaultCtor(ed);
        attachNested(ed, cm, pool, bodyClasses);
        return ed;
    }

    /**
     * The enum's constants in declaration order, read from {@code <clinit>}.
     *
     * <p>Order and arguments exist nowhere else: the constant FIELDS carry neither. The class each constant
     * instantiates is taken from the preceding {@code new}, which is how a constant with a body (an anonymous
     * subclass) is told apart from a plain one.
     */
    static List<EnumConst> enumConstants(ClassModel cm) {
        List<EnumConst> out = new ArrayList<>();
        MethodModel clinit = cm.methods().stream()
                .filter(m -> m.methodName().stringValue().equals("<clinit>")).findFirst().orElse(null);
        if (clinit == null) return out;
        CodeAttribute code = (CodeAttribute) clinit.findAttribute(Attributes.code()).orElse(null);
        if (code == null) return out;
        String selfDesc = "L" + cm.thisClass().asInternalName() + ";";

        // pass 1: constant name -> instantiated class, in bytecode (= declaration) order
        Map<String,String> subclassOf = new LinkedHashMap<>();
        String lastNew = null;
        for (CodeElement e : code) {
            if (e instanceof NewObjectInstruction n) lastNew = n.className().asInternalName();
            else if (e instanceof FieldInstruction f && f.opcode() == Opcode.PUTSTATIC
                    && f.typeSymbol().descriptorString().equals(selfDesc)) {
                subclassOf.put(f.name().stringValue(), lastNew);
                lastNew = null;
            }
        }
        if (subclassOf.isEmpty()) return out;

        // pass 2: the declaration arguments, by translating <clinit> and reading each constant's assignment
        Map<String,List<Expression>> argsOf = new HashMap<>();
        try {
            BlockStmt body = decompileClinit(cm, clinit, code);
            for (Statement s : body.findAll(Statement.class)) {
                if (!(s instanceof ExpressionStmt es) || !(es.getExpression() instanceof AssignExpr as)) continue;
                if (!(as.getTarget() instanceof FieldAccessExpr fa)) continue;
                if (!(as.getValue() instanceof ObjectCreationExpr oc)) continue;
                String cn = fa.getNameAsString();
                if (!subclassOf.containsKey(cn)) continue;
                // drop the synthetic leading (name, ordinal); what remains is what the source wrote
                List<Expression> a = new ArrayList<>(oc.getArguments());
                argsOf.put(cn, a.size() >= 2 ? new ArrayList<>(a.subList(2, a.size())) : List.of());
            }
        } catch (RuntimeException | StackOverflowError ignore) { }

        String self = cm.thisClass().asInternalName();
        for (Map.Entry<String,String> e : subclassOf.entrySet()) {
            String sub = e.getValue() == null || e.getValue().equals(self) ? null : e.getValue();
            out.add(new EnumConst(e.getKey(), argsOf.getOrDefault(e.getKey(), List.of()), sub));
        }
        return out;
    }

    /** Translate {@code <clinit>} to statements, purely to read the enum constant arguments out of it. */
    static BlockStmt decompileClinit(ClassModel cm, MethodModel clinit, CodeAttribute code) {
        Map<Integer,String> slotName = new HashMap<>(), slotDesc = new HashMap<>();
        List<LocalVariable> lvs = new ArrayList<>();
        List<LocalVariableType> lvts = new ArrayList<>();
        List<CodeElement> seq = new ArrayList<>();
        for (CodeElement e : code) {
            switch (e) {
                case LocalVariable lv -> { slotName.put(lv.slot(), lv.name().stringValue());
                                           slotDesc.put(lv.slot(), lv.type().stringValue()); lvs.add(lv); }
                case LocalVariableType lvt -> lvts.add(lvt);
                case Instruction i -> seq.add(i);
                case LabelTarget lt -> seq.add(lt);
                default -> { }
            }
        }
        Cfg cfg = null;
        try { cfg = buildCfg(cm, clinit); } catch (RuntimeException ignore) { }
        Locals locals = Locals.from(lvs, lvts, cfg != null ? cfg.labelPos : Map.of());
        return buildBody(cm, clinit, code, cfg, locals, new HashSet<>(), slotName, slotDesc, seq);
    }

    /**
     * True if this canonical constructor does no more than the field assignments the record header already
     * implies -- the only case where re-emitting it would be a pure duplicate.
     *
     * <p>A compact constructor's body compiles into the canonical constructor too, ahead of those
     * assignments. Skipping on canonical-ness alone therefore dropped that body outright, so
     * {@code record Range(int lo, int hi) { Range { if (lo > hi) ... } }} lost its normalisation and
     * {@code new Range(5, 2)} came back unswapped.
     *
     * <p>Recognising it by what the body CONTAINS rather than by counting instructions: anything beyond a
     * load, the super() call, a putfield to a component, and the return means real source code is present.
     */
    static boolean isImplicitCanonicalCtor(MethodModel m, RecordAttribute rec) {
        CodeAttribute code = (CodeAttribute) m.findAttribute(Attributes.code()).orElse(null);
        if (code == null) return true;
        Set<String> comps = new HashSet<>();
        for (RecordComponentInfo rc : rec.components()) comps.add(rc.name().stringValue());
        for (CodeElement e : code) {
            if (!(e instanceof Instruction in)) continue;
            boolean implied = switch (in) {
                case LoadInstruction li -> true;                    // `this`, or a parameter being stored
                case ReturnInstruction r -> true;
                case FieldInstruction fi -> fi.opcode() == Opcode.PUTFIELD
                        && comps.contains(fi.name().stringValue());
                case InvokeInstruction iv -> iv.opcode() == Opcode.INVOKESPECIAL
                        && iv.name().stringValue().equals("<init>");
                default -> false;
            };
            if (!implied) return false;
        }
        return true;
    }

    /** True if this constructor's parameters are exactly the record's components (the canonical one). */
    static boolean isCanonicalCtor(MethodModel m, RecordAttribute rec) {
        MethodTypeDesc mt = m.methodTypeSymbol();
        List<RecordComponentInfo> comps = rec.components();
        if (mt.parameterCount() != comps.size()) return false;
        for (int i = 0; i < comps.size(); i++)
            if (!mt.parameterType(i).descriptorString().equals(comps.get(i).descriptor().stringValue()))
                return false;
        return true;
    }

    /**
     * True if this method is a bridge: the erasure shim the compiler adds when a generic method is
     * implemented at a narrower type. It has no source form, and emitting it alongside the real method
     * produces two declarations that collide after erasure ({@code Integer get()} and {@code Object get()}).
     */
    static boolean isBridge(MethodModel m) {
        return m.flags().has(AccessFlag.BRIDGE) || m.flags().has(AccessFlag.SYNTHETIC);
    }

    /** Remove a leading {@code Objects.requireNonNull(...)}, the null check on a dropped hidden parameter. */
    static void dropLeadingNullCheck(BlockStmt body) {
        if (body.getStatements().isEmpty()) return;
        if (body.getStatement(0) instanceof ExpressionStmt es
                && es.getExpression() instanceof MethodCallExpr mc
                && mc.getNameAsString().equals("requireNonNull"))
            body.getStatements().remove(0);
    }

    /**
     * True if this constructor takes a hidden enclosing-instance parameter, i.e. it belongs to a non-static
     * inner class. Detected from the SIGNATURE rather than the presence of a this$0 field, because javac
     * omits that field when the inner class never reads the enclosing instance while still passing it.
     */
    static boolean isInnerCtor(ClassModel cm, MethodModel m, InnerClassInfo nested) {
        if (nested == null || nested.flags().contains(AccessFlag.STATIC)) return false;
        String self = cm.thisClass().asInternalName();
        int dollar = self.lastIndexOf('$');
        if (dollar < 0) return false;
        MethodTypeDesc d = m.methodTypeSymbol();
        return d.parameterCount() > 0
                && d.parameterType(0).descriptorString().equals("L" + self.substring(0, dollar) + ";");
    }

    /** True if this class is an enum (its supertype is {@code java.lang.Enum}). */
    static boolean isEnumClass(ClassModel cm) {
        return cm.superclass().map(ce -> ce.asInternalName().equals("java/lang/Enum")).orElse(false);
    }

    /**
     * Remove a no-argument constructor with an empty body.
     *
     * <p>Every class has a constructor in bytecode, so the compiler-generated default one is
     * indistinguishable from an explicitly written {@code C() {}} -- both compile to just the implicit
     * {@code super()}, which is dropped. Emitting it is harmless but noise, and the class keeps an implicit
     * default constructor either way, so the declaration is removed.
     */
    static void dropDefaultCtor(TypeDeclaration<?> decl) {
        decl.getMembers().removeIf(mem -> mem instanceof ConstructorDeclaration k
                && k.getParameters().isEmpty() && k.getBody().getStatements().isEmpty()
                && k.getTypeParameters().isEmpty() && k.getThrownExceptions().isEmpty());
    }

    /** True if this class is declared inside another one (so it must not become its own file). */
    static boolean isNestedClass(ClassModel cm) {
        String self = cm.thisClass().asInternalName();
        if (cm.findAttribute(Attributes.nestHost())
              .map(nh -> !nh.nestHost().asInternalName().equals(self)).orElse(false)) return true;
        if (cm.findAttribute(Attributes.enclosingMethod()).isPresent()) return true;
        for (InnerClassInfo ic : innerClassesOf(cm))
            if (ic.innerClass().asInternalName().equals(self) && ic.outerClass().isPresent()) return true;
        return false;
    }

    /**
     * The source name of a nested class. The {@code InnerClasses} entry carries the name as written, which
     * is what recovers {@code I} from the JVM's mangled {@code Outer$1I}; an anonymous class has no name
     * there and gets a synthetic one.
     */
    static String nestedName(InnerClassInfo ic, String internal) {
        return ic.innerName().map(Utf8Entry::stringValue).orElseGet(() -> simple(internal));
    }

    /**
     * Modifiers for a declaration. A nested class's own {@code access_flags} do NOT record {@code static} or
     * the private/protected access it was declared with -- those live in its {@code InnerClasses} entry, so
     * that is the source of truth when building it as a member.
     *
     * <p>{@code static} is only emitted for a plain nested class: interfaces, records and enums nested in a
     * type are implicitly static, and the keyword would be redundant.
     */
    static Modifier.Keyword[] nestedModifiers(ClassModel cm, InnerClassInfo ic, boolean isRecord) {
        if (ic == null) return classModifiers(cm);
        List<Modifier.Keyword> ks = new ArrayList<>();
        var f = ic.flags();
        if (f.contains(AccessFlag.PUBLIC))         ks.add(Modifier.Keyword.PUBLIC);
        else if (f.contains(AccessFlag.PRIVATE))   ks.add(Modifier.Keyword.PRIVATE);
        else if (f.contains(AccessFlag.PROTECTED)) ks.add(Modifier.Keyword.PROTECTED);
        boolean iface = f.contains(AccessFlag.INTERFACE);
        // A local or anonymous class has no InnerClasses outer entry; hoisting it to member position needs
        // `static` so it does not require an enclosing instance it no longer has one of.
        boolean hoisted = ic.outerClass().isEmpty();
        if (!isRecord && !iface && (f.contains(AccessFlag.STATIC) || hoisted)) ks.add(Modifier.Keyword.STATIC);
        if (!isRecord && !iface && f.contains(AccessFlag.FINAL) && !f.contains(AccessFlag.ABSTRACT))
            ks.add(Modifier.Keyword.FINAL);
        if (!isRecord && f.contains(AccessFlag.ABSTRACT) && !iface) ks.add(Modifier.Keyword.ABSTRACT);
        return ks.toArray(Modifier.Keyword[]::new);
    }

    /**
     * Emit a {@code record Name(components...)} for classes carrying a Record attribute. The
     * compiler-generated members (canonical ctor, accessors, and the ObjectMethods-backed
     * equals/hashCode/toString) are suppressed; any user-written methods are kept.
     */
    static RecordDeclaration buildRecord(ClassModel cm, RecordAttribute rec, String name,
                                         InnerClassInfo nested, Map<String,ClassModel> pool) {
        RecordDeclaration rd = new RecordDeclaration();
        rd.setName(name);
        // records are implicitly final; an explicit 'final' is illegal, so drop it
        List<Modifier.Keyword> mods = new ArrayList<>();
        for (Modifier.Keyword k : nestedModifiers(cm, nested, true))
            if (k != Modifier.Keyword.FINAL && k != Modifier.Keyword.ABSTRACT) mods.add(k);
        rd.setModifiers(mods.toArray(Modifier.Keyword[]::new));
        cm.findAttribute(Attributes.signature()).ifPresent(sa -> {
            try {
                ClassSignature cs = ClassSignature.parseFrom(sa.signature().stringValue());
                if (!cs.typeParameters().isEmpty()) rd.setTypeParameters(typeParams(cs.typeParameters()));
            } catch (RuntimeException ignore) { }
        });
        // records implicitly extend java.lang.Record; they may still implement interfaces
        List<ClassOrInterfaceType> recIfaces = interfaceTypes(cm, classSignature(cm));
        if (!recIfaces.isEmpty()) rd.setImplementedTypes(new NodeList<>(recIfaces));

        Set<String> compNames = new HashSet<>();
        for (RecordComponentInfo rc : rec.components()) {
            String cn = rc.name().stringValue();
            compNames.add(cn);
            Type ct = rc.findAttribute(Attributes.signature())
                    .map(sig -> { try { return sigType(Signature.parseFrom(sig.signature().stringValue())); }
                                  catch (RuntimeException e) { return null; } }).orElse(null);
            if (ct == null) ct = type(ClassDesc.ofDescriptor(rc.descriptor().stringValue()));
            rd.addParameter(ct, cn);
        }
        emitFields(rd, cm, compNames);     // static fields only (component backing fields are the header)

        for (MethodModel m : cm.methods()) {
            String n = m.methodName().stringValue();
            // A record body can declare static fields and initialisers just like a class, so its <clinit>
            // carries source too: dropping it left `static int STEP = 1;` reading zero.
            if (n.equals("<clinit>")) { emitStaticInit(rd, cm, m); continue; }
            // The record header already declares the canonical constructor, so re-emitting one with the
            // component types would be a duplicate. A NON-canonical constructor is kept (it delegates via
            // this(...) and is a real source member).
            if (n.equals("<init>") && isCanonicalCtor(m, rec) && isImplicitCanonicalCtor(m, rec)) continue;
            // skip synthetic accessors (name matches a component, no params)
            if (compNames.contains(n) && m.methodTypeSymbol().parameterCount() == 0) continue;
            // skip compiler-generated equals/hashCode/toString (backed by ObjectMethods bootstrap)
            if ((n.equals("equals") || n.equals("hashCode") || n.equals("toString")) && usesObjectMethods(m)) continue;
            decompileInto(rd, m, cm);
        }
        dropDefaultCtor(rd);
        attachNested(rd, cm, pool);
        return rd;
    }

    /** Parsed generic class signature (type params + generic supertypes), or null if absent/malformed. */
    static ClassSignature classSignature(ClassModel cm) {
        return cm.findAttribute(Attributes.signature())
                .map(sa -> { try { return ClassSignature.parseFrom(sa.signature().stringValue()); }
                             catch (RuntimeException e) { return null; } })
                .orElse(null);
    }

    /** Set {@code extends}/{@code implements} (and interface-ness) on a class declaration from the class file. */
    static void applySupertypes(ClassOrInterfaceDeclaration cls, ClassModel cm) {
        boolean isInterface = cm.flags().has(AccessFlag.INTERFACE);
        cls.setInterface(isInterface);
        if (isInterface)   // interfaces are implicitly abstract; `abstract interface` is redundant
            cls.getModifiers().removeIf(m -> m.getKeyword() == Modifier.Keyword.ABSTRACT);
        ClassSignature cs = classSignature(cm);
        if (!isInterface) {
            String sup = cm.superclass().map(ce -> ce.asInternalName()).orElse("java/lang/Object");
            if (!sup.equals("java/lang/Object")) {
                ClassOrInterfaceType t = (cs != null) ? citOf(renderSig(cs.superclassSignature()))
                                                      : citOf(cm.superclass().map(Decompiler::rawName).orElse(null));
                if (t != null) cls.setExtendedTypes(new NodeList<>(t));
            }
        }
        List<ClassOrInterfaceType> ifaces = interfaceTypes(cm, cs);
        if (!ifaces.isEmpty()) {
            if (isInterface) cls.setExtendedTypes(new NodeList<>(ifaces));   // an interface `extends` its supers
            else cls.setImplementedTypes(new NodeList<>(ifaces));
        }
    }

    /** The implemented/extended interface types, preferring the generic signature over erased names. */
    static List<ClassOrInterfaceType> interfaceTypes(ClassModel cm, ClassSignature cs) {
        List<ClassOrInterfaceType> ifaces = new ArrayList<>();
        if (cs != null && !cs.superinterfaceSignatures().isEmpty()) {
            for (Signature.ClassTypeSig s : cs.superinterfaceSignatures()) { ClassOrInterfaceType t = citOf(renderSig(s)); if (t != null) ifaces.add(t); }
        } else {
            for (var ie : cm.interfaces()) { ClassOrInterfaceType t = citOf(rawName(ie)); if (t != null) ifaces.add(t); }
        }
        return ifaces;
    }

    /** Emit field declarations (skipping synthetic and the given names, e.g. record component backing fields). */
    static void emitFields(TypeDeclaration<?> decl, ClassModel cm, Set<String> skipNames) {
        for (FieldModel fm : cm.fields()) {
            String name = fm.fieldName().stringValue();
            // Synthetic fields ARE emitted. They have no source form, but the bodies that reference them do
            // (an inner class's captured outer instance `this$0` and captured locals `val$x`, and the
            // `$assertionsDisabled` flag behind `assert`), and their names are legal identifiers, so leaving
            // them out only turns recovered code into unresolvable references. Ones that are genuinely
            // internal to a reconstructed form, like an enum's $VALUES, are excluded by name instead.
            if (skipNames.contains(name)) continue;
            Type ft = fm.findAttribute(Attributes.signature())
                    .map(sa -> { try { return sigType(Signature.parseFrom(sa.signature().stringValue())); }
                                 catch (RuntimeException e) { return null; } }).orElse(null);
            if (ft == null) ft = type(fm.fieldTypeSymbol());
            // [bytecode] Recover type-use annotations on the field's type (@Unbounded int n).
            for (var ta : typeAnnotationsOn(fm)) {
                if (ta.targetPath().isEmpty()
                        && ta.targetInfo().targetType() == java.lang.classfile.TypeAnnotation.TargetType.FIELD) {
                    annotateType(ft, ta.annotation());
                }
            }
            var fd = decl.addField(ft, name, fieldModifiers(fm));
            for (AnnotationExpr a : annotationsOn(fm)) fd.addAnnotation(a);
            Expression init = fm.findAttribute(Attributes.constantValue())
                    .map(cv -> constValue(cv.constant())).orElse(null);
            // A final field MUST be initialized -- always for an interface field (implicitly final), and for
            // a `static final` one because there is no constructor left to assign it. When the value is not a
            // compile-time constant it lives in <clinit>, which is not decompiled, so a type-default
            // placeholder keeps the declaration well-formed; the marker records that it is not recovered.
            boolean mustInit = cm.flags().has(AccessFlag.INTERFACE)
                    || (fm.flags().has(AccessFlag.STATIC) && fm.flags().has(AccessFlag.FINAL));
            if (init == null && mustInit) {
                init = defaultValue(fm.fieldTypeSymbol());
                fd.setComment(new LineComment(" NOTE initializer not recovered (assigned in <clinit>)"));
            }
            if (init != null) fd.getVariable(0).setInitializer(init);
        }
    }

    /** Fields whose stores are part of a desugaring, not of the source. */
    static final Set<String> SYNTHETIC_STATICS = Set.of("$assertionsDisabled", "$VALUES");

    /**
     * Recover a class's static initialization from {@code <clinit>}.
     *
     * <p>Source can write it two ways -- a field initializer, {@code static int[] log = new int[1];}, and a
     * {@code static { ... }} block -- and javac lowers both into the same method, in textual order. A leading
     * run of plain stores to this class's own fields is exactly what field initializers compile to, so it
     * folds back into the declarations; anything after that becomes a static block.
     *
     * <p>Leaving this out did not stop the recovered class compiling, since a field needs no initializer, but
     * every read then saw a default value instead of the intended one. An interface field is worse: it must
     * be initialized, so an unrecovered one got a placeholder that was silently the wrong value.
     */
    static void emitStaticInit(TypeDeclaration<?> decl, ClassModel cm, MethodModel clinit) {
        CodeAttribute code = (CodeAttribute) clinit.findAttribute(Attributes.code()).orElse(null);
        if (code == null) return;
        BlockStmt body;
        try { body = decompileClinit(cm, clinit, code); } catch (RuntimeException e) { return; }

        List<Statement> stmts = new ArrayList<>(body.getStatements());
        // A store to a synthetic static belongs to a desugaring that is recovered in its own right: the
        // $assertionsDisabled flag behind `assert`, an enum's $VALUES array.
        stmts.removeIf(s -> SYNTHETIC_STATICS.stream().anyMatch(s.toString()::contains));

        int i = 0;
        Set<String> folded = new HashSet<>();
        for (; i < stmts.size(); i++) {
            String f = ownStaticAssigned(stmts.get(i), decl);
            if (f == null || !folded.add(f)) break;
            FieldDeclaration fd = fieldByName(decl, f);
            if (fd == null || fd.getVariables().size() != 1) break;
            fd.getVariable(0).setInitializer(
                    ((AssignExpr) ((ExpressionStmt) stmts.get(i)).getExpression()).getValue());
            fd.removeComment();                       // the placeholder marker no longer applies
        }
        List<Statement> rest = new ArrayList<>(stmts.subList(i, stmts.size()));
        if (rest.isEmpty()) return;
        // An interface cannot carry a static block, so whatever is left there stays unrecovered.
        if (cm.flags().has(AccessFlag.INTERFACE)) return;
        decl.addMember(new InitializerDeclaration(true, new BlockStmt(new NodeList<>(rest))));
    }

    /** The own-class static field a statement assigns, if it is a plain {@code [Owner.]field = expr;}. */
    static String ownStaticAssigned(Statement s, TypeDeclaration<?> decl) {
        if (!(s instanceof ExpressionStmt es) || !(es.getExpression() instanceof AssignExpr as)) return null;
        if (as.getOperator() != AssignExpr.Operator.ASSIGN) return null;
        Expression t = as.getTarget();
        if (t instanceof NameExpr ne) return ne.getNameAsString();
        if (t instanceof FieldAccessExpr fa && fa.getScope() instanceof NameExpr sc
                && sc.getNameAsString().equals(decl.getNameAsString())) return fa.getNameAsString();
        return null;
    }

    static FieldDeclaration fieldByName(TypeDeclaration<?> decl, String name) {
        for (var m : decl.getMembers())
            if (m instanceof FieldDeclaration fd)
                for (var v : fd.getVariables())
                    if (v.getNameAsString().equals(name)) return fd;
        return null;
    }

    /** The type-default value, used as a placeholder where an initializer could not be recovered. */
    static Expression defaultValue(ClassDesc d) {
        if (!d.isPrimitive()) return new NullLiteralExpr();
        return switch (d.descriptorString()) {
            case "Z" -> new BooleanLiteralExpr(false);
            case "J" -> new LongLiteralExpr("0L");
            case "F" -> new DoubleLiteralExpr("0.0f");
            case "D" -> new DoubleLiteralExpr("0.0");
            case "C" -> new com.github.javaparser.ast.expr.CharLiteralExpr("\\u0000");
            default  -> new IntegerLiteralExpr("0");
        };
    }

    static Modifier.Keyword[] fieldModifiers(FieldModel fm) {
        List<Modifier.Keyword> ks = new ArrayList<>();
        var f = fm.flags();
        if (f.has(AccessFlag.PUBLIC))    ks.add(Modifier.Keyword.PUBLIC);
        if (f.has(AccessFlag.PRIVATE))   ks.add(Modifier.Keyword.PRIVATE);
        if (f.has(AccessFlag.PROTECTED)) ks.add(Modifier.Keyword.PROTECTED);
        if (f.has(AccessFlag.STATIC))    ks.add(Modifier.Keyword.STATIC);
        if (f.has(AccessFlag.FINAL))     ks.add(Modifier.Keyword.FINAL);
        if (f.has(AccessFlag.VOLATILE))  ks.add(Modifier.Keyword.VOLATILE);
        if (f.has(AccessFlag.TRANSIENT)) ks.add(Modifier.Keyword.TRANSIENT);
        return ks.toArray(Modifier.Keyword[]::new);
    }

    /** Render a ConstantValue attribute entry (compile-time constant field initializer) as a literal. */
    static Expression constValue(java.lang.classfile.constantpool.ConstantValueEntry cve) {
        return cve instanceof LoadableConstantEntry le ? constEntry(le) : null;
    }

    static String rawName(java.lang.classfile.constantpool.ClassEntry ce) {
        String printed = javaName(ce.asSymbol().displayName());
        imports.note(ce.asInternalName(), printed);
        return printed;
    }
    static ClassOrInterfaceType citOf(String s) {
        if (s == null) return null;
        try { return StaticJavaParser.parseClassOrInterfaceType(s); } catch (RuntimeException e) { return null; }
    }

    /** True if the method's code contains an invokedynamic backed by java.lang.runtime.ObjectMethods. */
    static boolean usesObjectMethods(MethodModel m) {
        CodeAttribute code = (CodeAttribute) m.findAttribute(Attributes.code()).orElse(null);
        if (code == null) return false;
        for (CodeElement e : code)
            if (e instanceof InvokeDynamicInstruction indy) {
                try {
                    if (indy.invokedynamic().bootstrap().bootstrapMethod().reference()
                            .owner().asInternalName().equals("java/lang/runtime/ObjectMethods")) return true;
                } catch (RuntimeException ignore) { }
            }
        return false;
    }

    static void sweep(Path dir, Path emit) throws Exception {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> w = Files.walk(dir)) {
            w.filter(p -> p.toString().endsWith(".class")).sorted().forEach(files::add);
        }
        if (emit != null) Files.createDirectories(emit);

        // Parse everything first: a nested class is emitted as part of its enclosing class, so building one
        // top-level class needs the other classfiles of the same compilation available by internal name.
        Map<String,ClassModel> pool = new LinkedHashMap<>();
        int errored = 0;
        for (Path p : files) {
            try {
                ClassModel cm = ClassFile.of().parse(p);
                pool.put(cm.thisClass().asInternalName(), cm);
            } catch (Exception e) { errored++; }
        }

        // `full` counts classes translated without hitting an unsupported instruction. It deliberately does
        // NOT also require the class to declare a method. That extra condition made sense when most classes
        // failed -- "nothing went wrong" is a negative test, and it is trivially true for a class with no
        // code, which would have flattered the number. Now that nearly everything translates it only
        // subtracts: 100 of the 1671 classes here declare nothing but a constructor (exception types, base
        // classes for initialization-order tests), translate correctly, and were being reported as failures.
        int classes = 0, methods = 0, full = 0, written = 0, nestedMerged = 0;
        List<String> fullClasses = new ArrayList<>();
        for (ClassModel cm : pool.values()) {
            classes++;
            long mc = cm.methods().stream().map(m -> m.methodName().stringValue())
                    .filter(n -> !n.equals("<init>") && !n.equals("<clinit>")).count();
            methods += mc;
            // Nested classes contribute their members to the enclosing class's file rather than one of their
            // own; they are still counted for coverage, since their bodies are translated either way.
            if (isNestedClass(cm)) {
                nestedMerged++;
                classUnsupported = 0;
                try { buildDecl(cm, pool, null); } catch (Exception e) { classUnsupported++; }
                if (classUnsupported == 0) { full++; fullClasses.add(cm.thisClass().asInternalName()); }
                continue;
            }
            classUnsupported = 0;
            CompilationUnit cu = null;
            try { cu = build(cm, pool); } catch (Exception e) { classUnsupported++; }
            if (emit != null && cu != null) {
                Path outFile = emit.resolve(cm.thisClass().asInternalName() + ".java");
                Files.createDirectories(outFile.getParent());
                Files.writeString(outFile, render(cu));
                written++;
            }
            if (classUnsupported == 0) { full++; fullClasses.add(cm.thisClass().asInternalName()); }
        }

        System.out.println("== bytecode-to-source coverage over " + dir + " ==");
        System.out.printf("scanned: %d classes (%d unparseable), %d non-ctor methods%n", classes, errored, methods);
        if (emit != null)
            System.out.printf("wrote %d recovered .java files under %s (%d nested classes merged into their outer)%n",
                    written, emit, nestedMerged);
        System.out.printf("fully translatable now (no unsupported instruction): %d classes (%.1f%%)%n",
                full, classes == 0 ? 0.0 : 100.0 * full / classes);
        System.out.println();
        // Printed only when there is something left to rank; an empty heading reads like a missing section.
        if (!unsupportedHist.isEmpty()) {
            System.out.println("unsupported-instruction histogram (quick-win ranking, by occurrences):");
            List<Map.Entry<String,Integer>> hist = new ArrayList<>(unsupportedHist.entrySet());
            hist.sort(Comparator.<Map.Entry<String,Integer>>comparingInt(Map.Entry::getValue).reversed());
            for (Map.Entry<String,Integer> e : hist) System.out.printf("  %-24s %d%n", e.getKey(), e.getValue());
            System.out.println();
        }
        System.out.printf("control-flow structurer: %d methods emitted structured; %d fell back to linear%n",
                structuredMethods, structFallbacks.values().stream().mapToInt(Integer::intValue).sum());
        System.out.printf("source spans (CRT): %d/%d statements carry line:col (%.1f%%); %d methods with out-of-order spans%n",
                spannedStmts, totalStmts, totalStmts == 0 ? 0.0 : 100.0 * spannedStmts / totalStmts,
                spanOrderViolations);
        if (!structFallbacks.isEmpty()) {
            List<Map.Entry<String,Integer>> fb = new ArrayList<>(structFallbacks.entrySet());
            fb.sort(Comparator.<Map.Entry<String,Integer>>comparingInt(Map.Entry::getValue).reversed());
            for (Map.Entry<String,Integer> e : fb) System.out.printf("    fallback: %-28s %d%n", e.getKey(), e.getValue());
        }
        if (DIAG && !examples.isEmpty()) {
            System.out.println();
            System.out.println("diagnostic examples (class#method) per failure category:");
            examples.forEach((cat, ex) -> System.out.printf("  %-34s %s%n", cat, String.join(", ", ex)));
        }
        System.out.println();
        System.out.println("examples of fully-translatable classes:");
        fullClasses.stream().limit(15).forEach(c -> System.out.println("  " + c.replace("org/strata/jverify/langref/", "")));
    }

    /** Emit {@code m} into {@code cls}, as a constructor when it is {@code <init>} and a method otherwise. */
    static void decompileInto(TypeDeclaration<?> cls, MethodModel m, ClassModel cm) {
        decompileInto(cls, m, cm, 0, false);
    }

    /**
     * As above. {@code skipParams} hides that many leading parameters from the emitted signature (they stay
     * bound in the body's scope), and {@code dropSuper} suppresses the {@code super(...)} call: both are
     * needed for an enum constructor, whose {@code (String name, int ordinal)} prologue is compiler-added
     * and cannot be written in source.
     */
    static void decompileInto(TypeDeclaration<?> cls, MethodModel m, ClassModel cm,
                              int skipParams, boolean dropSuper) {
        boolean ctor = m.methodName().stringValue().equals("<init>");
        curClass = simple(cm.thisClass().asInternalName()); curMethod = m.methodName().stringValue();
        CodeAttribute code = (CodeAttribute) m.findAttribute(Attributes.code()).orElse(null);
        MethodTypeDesc mt = m.methodTypeSymbol();
        // generic signature (type parameters + generic return/param types), if present
        MethodSignature msig = m.findAttribute(Attributes.signature()).map(sa -> {
            try { return MethodSignature.parseFrom(sa.signature().stringValue()); } catch (RuntimeException e) { return null; }
        }).orElse(null);

        CallableDeclaration<?> cd;
        if (ctor) {
            // A constructor is named after its class, which for a nested class is the source name recovered
            // from InnerClasses rather than the mangled binary one.
            ConstructorDeclaration k = new ConstructorDeclaration(cls.getNameAsString());
            k.setModifiers(methodModifiers(m, cm.flags().has(AccessFlag.INTERFACE)));
            cls.addMember(k);
            cd = k;
        } else {
            MethodDeclaration md = cls.addMethod(m.methodName().stringValue(), methodModifiers(m, cm.flags().has(AccessFlag.INTERFACE)));
            Type retType = msig != null ? sigType(msig.result()) : null;
            md.setType(retType != null ? retType : type(mt.returnType()));
            cd = md;
        }
        if (msig != null && !msig.typeParameters().isEmpty()) cd.setTypeParameters(typeParams(msig.typeParameters()));
        for (AnnotationExpr a : annotationsOn(m)) cd.addAnnotation(a);
        // `throws` lives in its own Exceptions attribute; without it a caller of a method that throws a
        // CHECKED exception does not compile, since javac cannot see that it has to be handled.
        // The generic signature is preferred when it declares throws: it preserves a type-variable
        // clause (`<X extends Exception> ... throws X`) that the Exceptions attribute erases to the
        // bound, and the reconstructed clause must show the variable for source fidelity.
        m.findAttribute(Attributes.exceptions()).ifPresent(ex -> {
            NodeList<ReferenceType> thrown = new NodeList<>();
            if (msig != null && !msig.throwableSignatures().isEmpty()) {
                for (var ts : msig.throwableSignatures()) {
                    if (sigType((Signature) ts) instanceof ClassOrInterfaceType cit) thrown.add(cit);
                }
            }
            if (thrown.isEmpty()) {
                for (var ce : ex.exceptions()) {
                    ClassOrInterfaceType t = citOf(rawName(ce));
                    if (t != null) thrown.add(t);
                }
            }
            if (!thrown.isEmpty()) cd.setThrownExceptions(thrown);
        });
        // An abstract or interface method has no Code attribute, hence no LocalVariableTable to name its
        // parameters -- but it still HAS parameters, so they are emitted from the descriptor with synthetic
        // names. Returning before this point dropped them and made every such method look zero-argument.
        if (code == null) {
            boolean sigOnly = msig != null && msig.arguments().size() == mt.parameterCount();
            for (int i = 0; i < mt.parameterCount(); i++) {
                Type gt = sigOnly ? sigType(msig.arguments().get(i)) : null;
                cd.addParameter(gt != null ? gt : type(mt.parameterType(i)), "p" + i);
            }
            if (cd instanceof MethodDeclaration md) md.removeBody();
            applyMethodTypeAnnotations(cd, cd instanceof MethodDeclaration x ? x : null, m, 0);
            return;
        }
        MethodDeclaration md = cd instanceof MethodDeclaration x ? x : null;

        Map<Integer,String> slotName = new HashMap<>();
        Map<Integer,String> slotDesc = new HashMap<>();
        List<LocalVariable> lvs = new ArrayList<>();
        List<LocalVariableType> lvts = new ArrayList<>();
        List<CodeElement> seq = new ArrayList<>();
        for (CodeElement e : code) {
            switch (e) {
                case LocalVariable lv -> { slotName.put(lv.slot(), lv.name().stringValue());
                                           slotDesc.put(lv.slot(), lv.type().stringValue()); lvs.add(lv); }
                case LocalVariableType lvt -> lvts.add(lvt);
                case Instruction i -> seq.add(i);
                case LabelTarget lt -> seq.add(lt);
                default -> { }
            }
        }

        // Strategy: straight-line methods keep the linear path (exact CRT spans). Methods with real
        // control flow (multiple blocks) go through the structurer (if/else/loops/switch/try). On any
        // structurer failure we roll back and fall back linearly.
        Cfg cfg = null;
        try { cfg = buildCfg(cm, m); } catch (RuntimeException ignore) { }
        Locals locals = Locals.from(lvs, lvts, cfg != null ? cfg.labelPos : Map.of());

        boolean isStatic = m.flags().has(AccessFlag.STATIC);
        Set<String> declared = new HashSet<>();
        int slot = isStatic ? 0 : 1;
        boolean sigArgs = msig != null && msig.arguments().size() == mt.parameterCount();
        for (int i = 0; i < mt.parameterCount(); i++) {
            ClassDesc pt = mt.parameterType(i);
            Type gt = sigArgs ? sigType(msig.arguments().get(i)) : null;
            Locals.Scope ps = locals.at(slot, 0);
            // A hidden parameter is still a live local in the body, so it is registered as declared even
            // though it is not written in the signature.
            // The fallback name must be the one localName() will produce for an unnamed slot, or the
            // signature and the body disagree: a synthetic capture parameter has no LocalVariableTable
            // entry, and naming it p0 here while the body reads it as v1 does not compile.
            if (i >= skipParams) {
                cd.addParameter(gt != null ? gt : type(pt),
                                ps != null ? ps.name() : name(slotName, slot));
                // ACC_VARARGS marks the last parameter as `T...`, which compiles to `T[]`. The flag is the
                // only record of how it was written, and only the declaration differs -- every call site
                // already passes the array javac built.
                if (i == mt.parameterCount() - 1 && m.flags().has(AccessFlag.VARARGS)) {
                    Parameter p = cd.getParameter(cd.getParameters().size() - 1);
                    if (p.getType() instanceof ArrayType at) { p.setType(at.getComponentType()); p.setVarArgs(true); }
                }
            }
            declared.add(ps != null ? slotVarKey(locals, slot, 0) : ("v" + slot));
            String d = pt.descriptorString();
            slot += (d.equals("J") || d.equals("D")) ? 2 : 1;
        }

        BlockStmt body = buildBody(cm, m, code, cfg, locals, declared, slotName, slotDesc, seq, dropSuper);
        // With a hidden parameter removed, the null check javac emits against it has nothing left to refer
        // to, so the guard goes with the parameter it guarded.
        if (ctor && skipParams > 0) dropLeadingNullCheck(body);
        if (md != null) md.setBody(body);
        else ((ConstructorDeclaration) cd).setBody(body);
        applyMethodTypeAnnotations(cd, md, m, skipParams);
    }

    /** [bytecode] Recover @Nat/@Unbounded-style type-use annotations from the classfile onto the
     *  reconstructed return and parameter types (METHOD_RETURN / METHOD_FORMAL_PARAMETER targets). */
    static void applyMethodTypeAnnotations(CallableDeclaration<?> cd, MethodDeclaration md,
                                           MethodModel m, int skipParams) {
        for (var ta : typeAnnotationsOn(m)) {
            if (!ta.targetPath().isEmpty()) continue;
            var ti = ta.targetInfo();
            if (ti.targetType() == java.lang.classfile.TypeAnnotation.TargetType.METHOD_RETURN && md != null) {
                annotateType(md.getType(), ta.annotation());
            } else if (ti instanceof java.lang.classfile.TypeAnnotation.FormalParameterTarget fp) {
                int idx = fp.formalParameterIndex() - skipParams;
                if (idx >= 0 && idx < cd.getParameters().size()) {
                    annotateType(cd.getParameter(idx).getType(), ta.annotation());
                }
            }
        }
    }

    static BlockStmt buildBody(ClassModel cm, MethodModel m, CodeAttribute code, Cfg cfg, Locals locals,
                               Set<String> declared, Map<Integer,String> slotName, Map<Integer,String> slotDesc,
                               List<CodeElement> seq) {
        return buildBody(cm, m, code, cfg, locals, declared, slotName, slotDesc, seq, false);
    }

    /** Build a method/lambda body: structured (if/loops/switch/try) when possible, else linear with CRT spans. */
    static BlockStmt buildBody(ClassModel cm, MethodModel m, CodeAttribute code, Cfg cfg, Locals locals,
                               Set<String> declared, Map<Integer,String> slotName, Map<Integer,String> slotDesc,
                               List<CodeElement> seq, boolean dropSuper) {
        // Source positions (javac -Xjcov). Available to BOTH the structured and the linear path: spans are
        // resolved per instruction (seq-index -> pc -> narrowest CRT statement range), not by pairing the
        // n-th statement with the n-th table entry, so reordering by the structurer cannot misalign them.
        List<CrtRange> crt = crtRanges(code);
        int[] seqToBci = bciTable(seq);
        boolean isCtor = m.methodName().stringValue().equals("<init>");
        String retDesc = m.methodTypeSymbol().returnType().descriptorString();
        // [bytecode] Local-variable type annotations, keyed by slot, for localType().
        Map<Integer, List<java.lang.classfile.Annotation>> slotAnnos = new HashMap<>();
        for (var ta : typeAnnotationsOn(code)) {
            if (!ta.targetPath().isEmpty()) continue;
            if (ta.targetInfo() instanceof java.lang.classfile.TypeAnnotation.LocalVarTarget lv) {
                for (var entry : lv.table()) {
                    slotAnnos.computeIfAbsent(entry.index(), k -> new ArrayList<>()).add(ta.annotation());
                }
            }
        }

        if (cfg != null && cfg.blocks.size() > 1) {
            int savedCU = classUnsupported;
            Map<String,Integer> savedHist = sweeping ? new TreeMap<>(unsupportedHist) : null;
            try {
                Dom dom = analyze(cfg);
                // Hoist locals whose first store sits in a block that does not dominate all their uses
                // (e.g. declared inside one switch case but read in another / after the switch).
                Set<String> preDeclared = new HashSet<>(declared);
                // NB: hoistedDecls adds the hoisted keys to preDeclared, so it must run BEFORE the Body is
                // constructed (the constructor copies the set); otherwise the first store re-declares them.
                List<Statement> hoisted = hoistedDecls(cfg, dom, locals, slotName, slotDesc, preDeclared,
                                                       crt, seqToBci);
                Body sbody = new Body(slotName, slotDesc, preDeclared);
                sbody.slotTypeAnnos = slotAnnos;
                sbody.locals = locals; sbody.cm = cm;
                sbody.crt = crt; sbody.seqToBci = seqToBci; sbody.inCtor = isCtor; sbody.retDesc = retDesc; sbody.dropSuper = dropSuper;
                BlockStmt structured = new Structurer(cfg, dom, sbody).structure();
                BlockStmt block = new BlockStmt();
                for (Statement hs : hoisted) block.addStatement(hs);
                for (Statement st : new ArrayList<>(structured.getStatements())) block.addStatement(st);
                annotateSpans(block);
                if (sweeping) { structuredMethods++; checkSpanOrder(block, hoisted.size()); }
                return block;
            } catch (RuntimeException | StackOverflowError ex) {  // roll back counters, then fall back linearly
                classUnsupported = savedCU;
                if (savedHist != null) { unsupportedHist.clear(); unsupportedHist.putAll(savedHist); }
                if (sweeping) {
                    String reason = ex instanceof StructException se && se.getMessage() != null
                            ? se.getMessage().replaceAll("B\\d+", "B#") : ex.getClass().getSimpleName();
                    structFallbacks.merge(reason, 1, Integer::sum);
                    noteExample("fallback:" + reason);
                }
            }
        }

        Body body = new Body(slotName, slotDesc, declared);
        body.slotTypeAnnos = slotAnnos;
        body.locals = locals; body.cm = cm;
        body.crt = crt; body.seqToBci = seqToBci; body.inCtor = isCtor; body.retDesc = retDesc; body.dropSuper = dropSuper;
        body.run(seq);

        BlockStmt block = new BlockStmt();
        for (Statement s : body.stmts) block.addStatement(s);   // spans already attached per instruction
        annotateSpans(block);
        return block;
    }

    /**
     * Render every recovered source span as a {@code @SR} marker comment, which {@link #render} turns into a
     * trailing {@code // src line:col-line:col}. Block statements are skipped (their span duplicates the
     * construct that owns them).
     */
    static void annotateSpans(BlockStmt root) {
        for (Statement s : root.findAll(Statement.class)) {
            if (s instanceof BlockStmt) continue;
            if (sweeping) totalStmts++;
            if (!s.containsData(SPAN)) continue;
            Span sp = s.getData(SPAN);
            if (sp == null || sp.isNone()) continue;
            if (sweeping) spannedStmts++;
            if (s.getComment().isEmpty()) s.setComment(new LineComment("@SR " + sp));
        }
    }

    /**
     * Diagnostic (sweep mode only): report when the emitted statement order disagrees with source line
     * order. Statement order is dictated by the CFG, never by the CRT -- reordering by line would be
     * unsound, since javac may emit bytecode whose order legitimately differs from the source. A
     * disagreement therefore signals a structuring bug worth inspecting, so it is counted, not "fixed".
     */
    static void checkSpanOrder(BlockStmt root, int skip) {
        int prevLine = 0, i = 0;
        for (Statement s : root.getStatements()) {
            if (i++ < skip) continue;        // hoisted declarations are deliberately lifted out of order
            if (s instanceof BlockStmt || !s.containsData(SPAN)) continue;
            Span sp = s.getData(SPAN);
            if (sp == null || sp.isNone()) continue;
            if (sp.beginLine() < prevLine) {
                spanOrderViolations++;
                noteExample("span-order-regression");
                return;                      // one report per method is enough
            }
            prevLine = sp.beginLine();
        }
    }

    /** The local-variable slot referenced by an instruction, or -1 if none. */
    static int slotOf(CodeElement e) {
        if (e instanceof LoadInstruction li) return li.slot();
        if (e instanceof StoreInstruction si) return si.slot();
        if (e instanceof IncrementInstruction ii) return ii.slot();
        return -1;
    }

    /**
     * Build declarations to hoist to the method top: a local must be pre-declared when the block of
     * its first store does not dominate every block that references it (so an inline declaration
     * would be trapped in a nested scope). Adds hoisted slots to {@code preDeclared}.
     */
    static List<Statement> hoistedDecls(Cfg cfg, Dom dom, Locals locals,
                                        Map<Integer,String> slotName, Map<Integer,String> slotDesc,
                                        Set<String> preDeclared, List<CrtRange> crt, int[] seqToBci) {
        // A local (keyed by slot+name+type) is hoisted when its first store does not dominate every
        // block that references it (e.g. assigned inside one switch case but read in another / after).
        Map<String,Integer> firstStoreBlk = new HashMap<>();  // key -> block of first store
        Map<String,Integer> firstStorePos = new HashMap<>();  // key -> seq-index of first store (for name/type)
        Map<String,Integer> keySlot = new HashMap<>();
        Map<String,Set<Integer>> refBlocks = new HashMap<>();
        for (Blk b : cfg.blocks) {
            for (int idx : b.instrs) {
                CodeElement e = cfg.seq.get(idx);
                int s = slotOf(e);
                if (s < 0) continue;
                String key = slotVarKey(locals, s, idx);
                keySlot.putIfAbsent(key, s);
                refBlocks.computeIfAbsent(key, k -> new HashSet<>()).add(b.id);
                if (e instanceof StoreInstruction && !firstStoreBlk.containsKey(key)) {
                    firstStoreBlk.put(key, b.id); firstStorePos.put(key, idx);
                }
            }
        }
        // A catch parameter is declared by its own catch clause, so it must never be hoisted -- and it looks
        // exactly like a candidate, because several catch clauses reuse one slot for differently-typed
        // exceptions and no single store dominates the others. Hoisting one produced a method-level
        // declaration that then collided with every `catch (X e)` naming it.
        Set<Integer> handlerEntry = new HashSet<>();
        for (Exc ex : cfg.excs) {
            Blk h = cfg.blocks.get(ex.handlerBlk());
            if (!h.instrs.isEmpty()) handlerEntry.add(h.instrs.get(0));
        }

        List<Statement> out = new ArrayList<>();
        for (String key : new java.util.TreeSet<>(firstStoreBlk.keySet())) {
            if (preDeclared.contains(key)) continue;         // parameter or already declared
            if (handlerEntry.contains(firstStorePos.get(key))) continue;    // catch parameter
            int slot = keySlot.get(key), dpos = firstStorePos.get(key);
            Locals.Scope sc = locals != null ? locals.at(slot, dpos) : null;
            if (sc == null && locals != null && locals.bySlot.containsKey(slot)) continue;  // synthetic (e.g. monitor temp) - don't hoist
            Type declType;
            if (sc != null && sc.genericType() != null) {
                try { declType = StaticJavaParser.parseType(sc.genericType()); } catch (RuntimeException e) { declType = null; }
            } else {
                String desc = sc != null ? sc.desc() : slotDesc.get(slot);
                declType = desc != null ? type(desc) : null;
            }
            if (declType == null) continue;                  // no known type -> leave to first-store `var`
            int db = firstStoreBlk.get(key);
            boolean dominatesAll = refBlocks.get(key).stream().allMatch(rb -> dom.dominates(db, rb));
            if (!dominatesAll) {
                String nm = sc != null ? sc.name() : name(slotName, slot);
                Statement decl = new ExpressionStmt(new VariableDeclarationExpr(declType, nm));
                attachSpan(decl, crt, seqToBci, dpos);            // the source line of its first store
                out.add(decl);
                preDeclared.add(key);
            }
        }
        return out;
    }

    /**
     * Scoped local-variable table: resolves a slot to a (name, descriptor) using the position, because
     * javac reuses one slot for several variables whose live ranges do not overlap.
     */
    static final class Locals {
        record Scope(int start, int end, String name, String desc, String genericType) { }
        final Map<Integer,List<Scope>> bySlot = new HashMap<>();
        void add(int slot, int start, int end, String name, String desc, String genericType) {
            bySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(new Scope(start, end, name, desc, genericType));
        }
        /** The scope for {@code slot} covering {@code pos} (or the nearest one, to absorb the declaring store). */
        /**
         * The scope for {@code slot} covering {@code pos}, tolerating the store or label immediately before a
         * scope opens (which is where a declaration's value is computed).
         *
         * <p>A scope about to OPEN is preferred over one that has just closed. Both can be within tolerance
         * of the same position when a slot is reused: a catch parameter's scope begins just after the astore
         * that binds it, exactly where the previous occupant's scope ended, and taking the closed one named
         * the exception after the variable it replaced.
         */
        Scope at(int slot, int pos) {
            List<Scope> ss = bySlot.get(slot);
            if (ss == null) return null;
            Scope fwd = null, back = null;
            int fd = Integer.MAX_VALUE, bd = Integer.MAX_VALUE;
            for (Scope s : ss) {
                if (pos >= s.start() && pos < s.end()) return s;                 // strictly in scope
                if (pos < s.start()) { int d = s.start() - pos; if (d < fd) { fd = d; fwd = s; } }
                else { int d = pos - s.end(); if (d < bd) { bd = d; back = s; } }
            }
            if (fwd != null && fd <= 3) return fwd;
            return (back != null && bd <= 3) ? back : null;
        }
        static Locals from(List<LocalVariable> lvs, List<LocalVariableType> lvts, Map<Label,Integer> labelPos) {
            // generic local types (LocalVariableTypeTable), keyed by slot@startScope
            Map<String,String> generics = new HashMap<>();
            for (LocalVariableType lvt : lvts) {
                Integer s = labelPos.get(lvt.startScope());
                if (s == null) continue;
                try { generics.put(lvt.slot() + "@" + s, renderSig(Signature.parseFrom(lvt.signature().stringValue()))); }
                catch (RuntimeException ignore) { }
            }
            Locals L = new Locals();
            for (LocalVariable lv : lvs) {
                Integer s = labelPos.get(lv.startScope()), e = labelPos.get(lv.endScope());
                int start = s != null ? s : 0;
                L.add(lv.slot(), start, e != null ? e : Integer.MAX_VALUE,
                        lv.name().stringValue(), lv.type().stringValue(), generics.get(lv.slot() + "@" + start));
            }
            return L;
        }
    }

    /** Per-method body translator: operand-stack simulation with a boolean-materialization peephole. */
    static final class Body {
        final Deque<Expression> stack = new ArrayDeque<>();
        final List<Statement> stmts = new ArrayList<>();
        final Set<String> declared;              // variable keys already declared (slot@scopeStart, or v<slot>)
        final Map<Integer,String> slotName, slotDesc;
        final Map<Expression,String> pendingNew = new IdentityHashMap<>();
        final Set<Expression> bools = Collections.newSetFromMap(new IdentityHashMap<>());
        /** 3-way comparison result (lcmp/fcmp/dcmp) -> its two operands, for rebuilding `a <op> b`. */
        final Map<Expression,Expression[]> cmp3 = new IdentityHashMap<>();
        final Map<Expression,Boolean> cmp3NanHigh = new IdentityHashMap<>();  // float/double cmp: NaN -> +1?
        /**
         * The JVM type descriptor of a pushed value, where it is known. Needed because bytecode erases
         * {@code boolean} to {@code int} (and {@code baload} serves both {@code byte[]} and
         * {@code boolean[]}), so the static type is the only way to tell that {@code flags[0] == 0} is
         * really a boolean comparison and must be printed as {@code == false}.
         */
        final Map<Expression,String> descOf = new IdentityHashMap<>();
        String retDesc;                            // enclosing method's return descriptor, for `return`
        Locals locals;                           // scoped local table (nullable); resolved at `pos`
        int pos = -1;                            // current seq-index, for scoped local resolution
        ClassModel cm;                           // enclosing class, for resolving lambda impl methods
        List<CrtRange> crt = List.of();           // statement ranges from the CharacterRangeTable
        int[] seqToBci;                           // seq-index -> bytecode pc (bridge to `crt`)
        boolean inCtor;                            // translating <init>: a bare <init> call is super()/this()
        boolean dropSuper;                         // suppress super(...) entirely (enum's synthetic prologue)
        final Map<Integer,String> slotAlias = new HashMap<>();   // slot -> minted name for an unnamed local
        final Map<Integer,String> slotType = new HashMap<>();    // slot -> descriptor it currently holds
        int synthCounter;                          // supplies the unique suffix for those names
        // [bytecode] A mid-expression iinc waiting to fuse with the next load of the same slot as a
        // PREFIX increment. `x++ + ++x` is `iload x; iinc; iinc; iload x; iadd`: the second iinc
        // pairs with the SECOND load (++x). Emitting it as a standalone `x += 1` statement instead
        // reorders it before the whole expression and changes the value the first operand read.
        int pendingIncSlot = -1;
        int pendingIncDelta;
        // [bytecode] Type-use annotations on locals (LOCAL_VARIABLE target in the Code attribute),
        // keyed by slot, so `@Nat int x = 5` keeps its annotation on the recovered declaration.
        Map<Integer, List<java.lang.classfile.Annotation>> slotTypeAnnos = Map.of();

        Body(Map<Integer,String> slotName, Map<Integer,String> slotDesc, Set<String> declared) {
            this.slotName = slotName; this.slotDesc = slotDesc; this.declared = new HashSet<>(declared);
        }

        /** The innermost source statement span covering the instruction at {@code seqIdx}, else NONE. */
        Span spanAt(int seqIdx) { return spanOf(crt, seqToBci, seqIdx); }

        /** Record the source span of the instruction at {@code seqIdx} on a statement (no-op if unknown). */
        void attachSpan(Statement s, int seqIdx) { Decompiler.attachSpan(s, crt, seqToBci, seqIdx); }

        /** Emit a translated statement, tagging it with the source span of the instruction that produced it. */
        void addStmt(Statement s) { attachSpan(s, pos); stmts.add(s); }

        /** Record a pushed value's static type; a {@code boolean} is also registered as a boolean expression. */
        void noteDesc(Expression e, String desc) {
            if (desc == null) return;
            descOf.put(e, desc);
            if (desc.equals("Z")) bools.add(e);
        }

        /**
         * Adapt a value to a target type, in EITHER direction across the boolean/int erasure.
         *
         * <p>An int 0/1 going into a boolean becomes {@code false}/{@code true}. The reverse also happens:
         * {@code int x = t ? 1 : 0} compiles to the same materialization diamond as a plain boolean, which
         * the structurer folds back to just the condition -- correct when the consumer wants a boolean, but
         * for an int target the ternary has to be restored or the value is a boolean in an int slot.
         */
        /**
         * Replace an unconsumed read of {@code nm} on the stack with an increment expression over it.
         * Returns false, changing nothing, if the stack top is not that read.
         */
        boolean replaceStackedRead(String nm, UnaryExpr.Operator op) {
            if (stack.isEmpty()) return false;
            if (!(stack.peek() instanceof NameExpr ne) || !ne.getNameAsString().equals(nm)) return false;
            Expression old = stack.pop();
            Expression inc = new UnaryExpr(new NameExpr(nm), op);
            stack.push(inc);
            String d = descOf.get(old);
            if (d != null) noteDesc(inc, d);
            return true;
        }

        /**
         * Recover {@code x++} / {@code ++x} from a store whose value updates the same slot by one while a
         * value is still live on the stack.
         *
         * <p>Which one it is falls out of what the leftover is. For {@code y = x++} javac pushes the old
         * value first and computes the update above it, so the leftover is a bare read of x. For
         * {@code y = ++x} it computes the update and dups THAT, so the leftover is the update expression
         * itself -- which is why the naive translation was wrong in a way plain reordering could not fix:
         * emitting {@code x = x + 1} and then reusing the duplicated {@code x + 1} evaluates it against the
         * already-incremented x, giving 7 where the source said 6.
         *
         * <p>Only a step of one qualifies; anything else stays a compound assignment. Returns true when the
         * store has been absorbed into the stacked expression and must not be emitted.
         */
        boolean asIncrementExpr(int slot, Expression val) {
            return asIncrementExprOn(new NameExpr(localName(slot)), val);
        }

        /**
         * The same recovery for any assignable target, not just a local: a field behaves identically, only
         * the store instruction differs. {@code log[idx++] = v} stores {@code idx + 1} through putstatic
         * while the OLD idx stays on the stack as the array index, so emitting the update as a statement
         * moved it ahead of the read and indexed one slot too far.
         */
        boolean asIncrementExprOn(Expression target, Expression val) {
            int step = selfUpdateStep(target, val);
            if (step == 0 || stack.isEmpty()) return false;
            boolean post = sameRef(stack.peek(), target);       // old value pushed before the update
            if (!post && !sameRef(stack.peek(), val)) return false;   // nor the dup'd update: not an increment
            Expression old = stack.pop();
            Expression inc = new UnaryExpr(target, step > 0
                    ? (post ? UnaryExpr.Operator.POSTFIX_INCREMENT : UnaryExpr.Operator.PREFIX_INCREMENT)
                    : (post ? UnaryExpr.Operator.POSTFIX_DECREMENT : UnaryExpr.Operator.PREFIX_DECREMENT));
            stack.push(inc);
            String d = descOf.get(old);
            if (d != null) noteDesc(inc, d);
            return true;
        }

        /** +1 or -1 if {@code val} is {@code target ± 1}, allowing the narrowing cast byte/short/char takes. */
        int selfUpdateStep(Expression target, Expression val) {
            Expression e = val instanceof CastExpr ce ? ce.getExpression() : val;
            if (e instanceof EnclosedExpr en) e = en.getInner();
            if (!(e instanceof BinaryExpr be)) return 0;
            boolean plus = be.getOperator() == BinaryExpr.Operator.PLUS;
            if (!plus && be.getOperator() != BinaryExpr.Operator.MINUS) return 0;
            if (!sameRef(be.getLeft(), target)) return 0;
            return isOne(be.getRight()) ? (plus ? 1 : -1) : 0;
        }

        /** Same reference as written. Both sides are built from one descriptor, so they render alike. */
        static boolean sameRef(Expression a, Expression b) { return a.toString().equals(b.toString()); }

        /** True for a literal one, in any of the numeric spellings javac may have used. */
        static boolean isOne(Expression e) {
            if (e instanceof IntegerLiteralExpr i) return i.getValue().equals("1");
            if (e instanceof LongLiteralExpr l) return l.getValue().replaceAll("[lL]$", "").equals("1");
            if (e instanceof DoubleLiteralExpr d) {
                String v = d.getValue().replaceAll("[dDfF]$", "");
                return v.equals("1") || v.equals("1.0");
            }
            return false;
        }

        Expression coerceTo(Expression v, String desc) {
            if (desc == null) return v;
            if (desc.equals("Z")) return coerce(v, desc);
            if (isIntLike(desc) && isBool(v))
                return new ConditionalExpr(par(v), new IntegerLiteralExpr("1"), new IntegerLiteralExpr("0"));
            return v;
        }

        /**
         * Coerce a method ARGUMENT to its parameter type. Unlike an assignment, a method call gets no
         * implicit narrowing: {@code byte b = 5} is legal but {@code Byte.valueOf(5)} is not, and bytecode
         * pushes both as an int, so the cast has to be put back for the narrow primitive types. Skipped when
         * the argument's static type is already known to match, to avoid redundant casts.
         */
        Expression coerceArg(Expression v, String desc) {
            v = coerce(v, desc);
            // A bare `null` argument does not pin down an overload: the compiler picks the most specific
            // applicable one, which need not be the one the bytecode actually calls. `String.valueOf(null)`
            // binds to valueOf(char[]) and throws, where the call recorded here is valueOf(Object). The
            // declared parameter type restores the choice the original source must have made.
            if (v instanceof NullLiteralExpr && (desc.startsWith("L") || desc.startsWith("[")))
                return new CastExpr(type(ClassDesc.ofDescriptor(desc)), v);
            String narrow = switch (desc) {
                case "B" -> "byte"; case "C" -> "char"; case "S" -> "short"; default -> null;
            };
            if (narrow == null || desc.equals(descOf.get(v))) return v;
            return new CastExpr(StaticJavaParser.parseType(narrow), par(v));
        }

        /**
         * Build a cast, merging stacked casts on a lambda into an INTERSECTION cast.
         *
         * <p>{@code (Runnable & Serializable) () -> {}} compiles to consecutive checkcasts, and emitting them
         * nested does not compile: the inner one would cast a lambda to {@code Serializable}, which is not a
         * functional interface. Only a lambda or method reference is folded this way -- stacked casts on an
         * ordinary value are meaningful on their own and are left alone.
         */
        Expression castTo(Type t, Expression e) {
            if (!(t instanceof ReferenceType rt) || !(e instanceof CastExpr inner)) return new CastExpr(t, e);
            NodeList<ReferenceType> types = new NodeList<>();
            types.add(rt);
            Expression target = inner;
            while (target instanceof CastExpr ce) {
                if (!(ce.getType() instanceof ReferenceType crt)) return new CastExpr(t, e);
                types.add(crt);
                target = ce.getExpression();
            }
            if (!(target instanceof LambdaExpr) && !(target instanceof MethodReferenceExpr))
                return new CastExpr(t, e);
            return new CastExpr(new com.github.javaparser.ast.type.IntersectionType(types), target);
        }

        /** The element descriptor of an array-typed expression, or null when its type is unknown. */
        String elementDesc(Expression arr) {
            String d = descOf.get(arr);
            return d != null && d.startsWith("[") ? d.substring(1) : null;
        }

        String localName(int slot) {
            if (locals != null) {
                Locals.Scope s = locals.at(slot, pos);
                if (s != null) return s.name();
                String alias = slotAlias.get(slot);
                if (alias != null) return alias;
                if (locals.bySlot.containsKey(slot)) return "v" + slot;   // slot named in another scope -> synthetic here
            }
            String alias = slotAlias.get(slot);
            return alias != null ? alias : name(slotName, slot);
        }

        /**
         * A method-unique name for a compiler-generated local that has no LocalVariableTable entry, and so no
         * source name. Naming these {@code v<slot>} collided whenever two sibling statements reused a slot
         * number -- try-with-resources generates a {@code Throwable} temp per statement -- and since the name
         * appears nowhere in the source, any distinct one is equivalent and strictly safer.
         */
        String freshName(int slot) {
            String nm = "t$" + (++synthCounter);
            slotAlias.put(slot, nm);
            return nm;
        }
        String localDesc(int slot) {
            if (locals != null) {
                Locals.Scope s = locals.at(slot, pos);
                if (s != null) return s.desc();
                if (locals.bySlot.containsKey(slot)) return null;         // type unknown here -> var
            }
            return slotDesc.get(slot);
        }
        /** Declared type of a local, with recovered type-use annotations attached. */
        Type localType(int slot) {
            Type t = localTypeRaw(slot);
            var annos = slotTypeAnnos.get(slot);
            if (annos != null) for (var an : annos) annotateType(t, an);
            return t;
        }
        /** Declared type of a local: generic (LocalVariableTypeTable) if known, else erased, else var. */
        Type localTypeRaw(int slot) {
            if (locals != null) {
                Locals.Scope s = locals.at(slot, pos);
                if (s != null) {
                    if (s.genericType() != null) { try { return StaticJavaParser.parseType(s.genericType()); } catch (RuntimeException ignore) { } }
                    return s.desc() != null ? type(s.desc()) : StaticJavaParser.parseType("var");
                }
                if (locals.bySlot.containsKey(slot)) return StaticJavaParser.parseType("var");
            }
            return type(slotDesc.get(slot));
        }
        /**
         * Declaration-tracking key: (slot, name, type), so one variable with several liveness scopes declares
         * once. A minted name gets its own key, so a slot renamed after a type change is declared afresh
         * rather than treated as an assignment to the previous occupant.
         */
        String varKey(int slot) {
            String alias = slotAlias.get(slot);
            if (alias != null && (locals == null || locals.at(slot, pos) == null)) return "alias:" + alias;
            return slotVarKey(locals, slot, pos);
        }

        void run(List<CodeElement> seq) {
            int k = 0;
            while (k < seq.size()) {
                try {
                    pos = k;                       // seq-index, for scoped local resolution
                    CodeElement e = seq.get(k);
                    if (e instanceof LabelTarget) { k++; continue; }
                    Instruction insn = (Instruction) e;
                    if (insn instanceof BranchInstruction br && isConditionalBranch(br.opcode())) {
                        int consumed = tryBooleanRegion(seq, k, br);
                        if (consumed > 0) { k += consumed; continue; }
                    }
                    int c;
                    if ((c = tryArrayInit(seq, null, k)) > 0)      { k += c; continue; }
                    if ((c = tryCompoundAssign(seq, null, k)) > 0) { k += c; continue; }
                    translate(insn);
                    k++;
                } catch (RuntimeException ex) {
                    if (sweeping) { unsupportedHist.merge("(stack/other error)", 1, Integer::sum); classUnsupported++;
                                    noteExample("(stack/other error):" + ex.getClass().getSimpleName()); }
                    addStmt(todo("recovery error"));
                    k++;
                }
            }
        }

        static int seqAt(List<Integer> idxs, int p) { return idxs == null ? p : idxs.get(p); }

        /** Translate a straight-line instruction list (with parallel seq-indices for scoped locals). */
        void translateInstrs(List<CodeElement> ins, List<Integer> idxs) { translateSpan(ins, idxs, 0, ins.size()); }
        void translateSpan(List<CodeElement> seq, List<Integer> idxs, int from, int to) {
            for (int p = from; p < to; ) {
                pos = seqAt(idxs, p);              // real seq-index of this element, for scoped local resolution
                CodeElement e = seq.get(p);
                if (e instanceof BranchInstruction br && br.opcode() == Opcode.GOTO) { p++; continue; }  // structural
                int c;
                if ((c = tryArrayInit(seq, idxs, p)) > 0)      { p += c; continue; }
                if ((c = tryCompoundAssign(seq, idxs, p)) > 0) { p += c; continue; }
                translate((Instruction) e);
                p++;
            }
        }

        /** Insert a duplicate of {@code top} {@code depth} slots below the top (DUP / DUP_X1 / DUP_X2 family). */
        void insertDup(Expression top, int depth) {
            if (stack.isEmpty()) { stack.push(top); return; }
            stack.pop();                                   // remove the original top (== top)
            Expression[] below = new Expression[depth];
            for (int i = 0; i < depth; i++) below[i] = stack.isEmpty() ? new NameExpr("?") : stack.pop();
            stack.push(top);                               // the duplicate goes underneath
            for (int i = depth - 1; i >= 0; i--) stack.push(below[i]);
            stack.push(top);                               // original back on top
        }

        static boolean isDup(CodeElement e)  { return e instanceof StackInstruction s && s.opcode() == Opcode.DUP; }
        static boolean isDup2(CodeElement e) { return e instanceof StackInstruction s && s.opcode() == Opcode.DUP2; }

        /** Array initializer: {@code <size> newarray (dup <i> <value> Xastore)+ } => {@code new T[]{v0, v1, ...}}. */
        int tryArrayInit(List<CodeElement> seq, List<Integer> idxs, int k) {
            if (!(seq.get(k) instanceof Instruction na)) return 0;
            Type elemType; String elemDesc = null;
            if (na instanceof NewPrimitiveArrayInstruction p) {
                elemType = StaticJavaParser.parseType(primName(p.typeKind()));
                if (p.typeKind() == TypeKind.BOOLEAN) elemDesc = "Z";
            } else if (na instanceof NewReferenceArrayInstruction r) {
                elemType = type(r.componentType().asSymbol());
            } else return 0;
            if (k + 1 >= seq.size() || !isDup(seq.get(k + 1)) || stack.isEmpty()) return 0;
            List<Expression> elems = new ArrayList<>();
            int p = k + 1;
            while (p + 1 < seq.size() && isDup(seq.get(p)) && isIntConst(seq.get(p + 1), elems.size())) {
                int j = p + 2;
                while (j < seq.size() && !(seq.get(j) instanceof ArrayStoreInstruction)) {
                    pos = seqAt(idxs, j);
                    int c;
                    if ((c = tryArrayInit(seq, idxs, j)) > 0) { j += c; continue; }       // nested (multi-dim) init
                    if ((c = tryCompoundAssign(seq, idxs, j)) > 0) { j += c; continue; }
                    translate((Instruction) seq.get(j)); j++;
                }
                if (j >= seq.size() || stack.isEmpty()) return 0;
                elems.add(coerce(stack.pop(), elemDesc));
                p = j + 1;
            }
            if (elems.isEmpty()) return 0;
            stack.pop();                                   // discard the size (implied by the initializer)
            stack.push(new ArrayCreationExpr(elemType, new NodeList<>(new ArrayCreationLevel()),
                                             new ArrayInitializerExpr(new NodeList<>(elems))));
            return p - k;
        }

        /**
         * Compound assignment idioms (evaluate the target once): {@code a[i] op= rhs} via
         * {@code dup2 Xaload <rhs> <binop> Xastore}, and {@code o.f op= rhs} via
         * {@code dup getfield <rhs> <binop> putfield}.
         */
        int tryCompoundAssign(List<CodeElement> seq, List<Integer> idxs, int k) {
            if (!(seq.get(k) instanceof Instruction in)) return 0;
            if (isDup2(in) && k + 1 < seq.size() && seq.get(k + 1) instanceof ArrayLoadInstruction && stack.size() >= 2) {
                int j = k + 2;
                while (j < seq.size() && !(seq.get(j) instanceof ArrayStoreInstruction)) j++;
                if (j >= seq.size() || !(seq.get(j - 1) instanceof OperatorInstruction bop)) return 0;
                AssignExpr.Operator aop = assignOp(bop.opcode());
                if (aop == null) return 0;
                Expression idx = stack.pop(), arr = stack.pop();
                translateSpan(seq, idxs, k + 2, j - 1);
                if (stack.isEmpty()) return 0;
                Expression rhs = stack.pop();
                addStmt(new ExpressionStmt(new AssignExpr(new ArrayAccessExpr(scopeOf(arr), idx), rhs, aop)));
                return (j + 1) - k;
            }
            if (isDup(in) && k + 1 < seq.size() && seq.get(k + 1) instanceof FieldInstruction gf
                    && gf.opcode() == Opcode.GETFIELD && !stack.isEmpty()) {
                // [bytecode] `y = c.n++` compiles to `aload; dup; getfield; DUP_X1; iconst_1; iadd;
                // putfield` -- same head and tail as a compound assignment, but the dup_x1 right
                // after the getfield marks the value-postfix form. Decline so the ordinary
                // translation (whose putfield arm absorbs the store via asIncrementExprOn)
                // recovers `c.n++` instead of mangling the receiver.
                if (k + 2 < seq.size() && seq.get(k + 2) instanceof StackInstruction sx
                        && (sx.opcode() == Opcode.DUP_X1 || sx.opcode() == Opcode.DUP_X2)) return 0;
                int j = k + 2;
                while (j < seq.size() && !(seq.get(j) instanceof FieldInstruction f && f.opcode() == Opcode.PUTFIELD)) j++;
                if (j >= seq.size() || !sameField(gf, (FieldInstruction) seq.get(j))) return 0;
                if (!(seq.get(j - 1) instanceof OperatorInstruction bop)) return 0;
                AssignExpr.Operator aop = assignOp(bop.opcode());
                if (aop == null) return 0;
                Expression recv = stack.pop();
                Expression lhs = new FieldAccessExpr(scopeOf(recv), gf.name().stringValue());
                translateSpan(seq, idxs, k + 2, j - 1);
                if (stack.isEmpty()) return 0;
                Expression rhs = stack.pop();
                addStmt(new ExpressionStmt(new AssignExpr(lhs, coerce(rhs, gf.typeSymbol().descriptorString()), aop)));
                return (j + 1) - k;
            }
            return 0;
        }
        static boolean sameField(FieldInstruction a, FieldInstruction b) {
            return a.owner().asInternalName().equals(b.owner().asInternalName()) && a.name().equals(b.name());
        }

        /** [bytecode] Emit a deferred mid-expression iinc as the statement it would have been. */
        void drainPendingInc() {
            if (pendingIncSlot < 0) return;
            int slot = pendingIncSlot;
            int c = pendingIncDelta;
            pendingIncSlot = -1;
            addStmt(new ExpressionStmt(new AssignExpr(new NameExpr(localName(slot)),
                    new IntegerLiteralExpr(String.valueOf(Math.abs(c))),
                    c >= 0 ? AssignExpr.Operator.PLUS : AssignExpr.Operator.MINUS)));
        }

        void translate(Instruction in) {
            // [bytecode] A pending iinc either fuses with a load of its slot (prefix ++x) or is
            // emitted before whatever comes next.
            if (pendingIncSlot >= 0 && !(in instanceof LoadInstruction li && li.slot() == pendingIncSlot)) {
                drainPendingInc();
            }
            switch (in) {
                case ConstantInstruction ci -> stack.push(constant(ci));
                case LoadInstruction li -> {
                    if (pendingIncSlot == li.slot()) {
                        int c = pendingIncDelta;
                        pendingIncSlot = -1;
                        Expression e = new UnaryExpr(new NameExpr(localName(li.slot())),
                                c > 0 ? UnaryExpr.Operator.PREFIX_INCREMENT : UnaryExpr.Operator.PREFIX_DECREMENT);
                        stack.push(e);
                        noteDesc(e, localDesc(li.slot()));
                        break;
                    }
                    Expression e = new NameExpr(localName(li.slot()));
                    stack.push(e);
                    noteDesc(e, localDesc(li.slot()));
                }
                case StoreInstruction si -> {
                    String desc = localDesc(si.slot());
                    Expression val = coerceTo(stack.pop(), desc);
                    // javac reuses a slot with no LocalVariableTable entry for values of DIFFERENT types --
                    // a record-pattern match holds a component and a nested match's value in the same slot.
                    // One name per slot then declares a variable assigned two incompatible types, so a
                    // change of type starts a new name.
                    if (desc == null) {
                        String vd = descOf.get(val);
                        if (vd != null && !vd.equals(slotType.get(si.slot()))) {
                            freshName(si.slot());
                            slotType.put(si.slot(), vd);
                        }
                    }
                    String nm = localName(si.slot());
                    // An update of this slot that ALSO leaves a value on the stack is an increment
                    // expression, not a statement. Emitting the update separately reorders it against the
                    // read: `y = x++` became `x += 1; y = x`, handing y the new value.
                    if (asIncrementExpr(si.slot(), val)) break;
                    if (declared.add(varKey(si.slot())))
                        addStmt(new ExpressionStmt(new VariableDeclarationExpr(
                                new VariableDeclarator(localType(si.slot()), nm, val))));
                    else
                        addStmt(new ExpressionStmt(new AssignExpr(new NameExpr(nm), val, AssignExpr.Operator.ASSIGN)));
                }
                case IncrementInstruction ii -> {
                    String nm = localName(ii.slot());
                    int c = ii.constant();
                    // `y = x++` on an int is `iload x; iinc x,1; istore y`: the old value is already on the
                    // stack, so the iinc is that read's postfix operator rather than a statement of its own.
                    if ((c == 1 || c == -1) && replaceStackedRead(nm, c > 0
                            ? UnaryExpr.Operator.POSTFIX_INCREMENT : UnaryExpr.Operator.POSTFIX_DECREMENT))
                        break;
                    // [bytecode] Mid-expression (stack non-empty) and not a postfix on the stacked read:
                    // this iinc belongs to a LATER read of the slot (prefix form). Defer it; emitting
                    // a statement here would reorder it before the expression under construction.
                    if ((c == 1 || c == -1) && !stack.isEmpty()) {
                        pendingIncSlot = ii.slot();
                        pendingIncDelta = c;
                        break;
                    }
                    addStmt(new ExpressionStmt(new AssignExpr(new NameExpr(nm),
                            new IntegerLiteralExpr(String.valueOf(Math.abs(c))),
                            c >= 0 ? AssignExpr.Operator.PLUS : AssignExpr.Operator.MINUS)));
                }
                case OperatorInstruction oi -> operator(oi);
                case ConvertInstruction cv -> {
                    // A cast binds tighter than any binary operator, so the operand needs parentheses:
                    // `i2b` over `x + 1` is `(byte) (x + 1)`, and `(byte) x + 1` would be an int again.
                    Expression e = new CastExpr(StaticJavaParser.parseType(convTarget(cv.opcode())),
                                                par(stack.pop()));
                    stack.push(e);
                    noteDesc(e, convDesc(cv.opcode()));
                }
                case TypeCheckInstruction tc -> {
                    Type t = type(tc.type().asSymbol());
                    Expression e = stack.pop();
                    if (tc.opcode() == Opcode.CHECKCAST) stack.push(castTo(t, e));
                    else { InstanceOfExpr io = new InstanceOfExpr(e, (ReferenceType) t); stack.push(io); bools.add(io); }
                }
                case FieldInstruction fi -> field(fi);
                case ArrayLoadInstruction al -> {
                    Expression idx = stack.pop(); Expression arr = stack.pop();
                    Expression e = new ArrayAccessExpr(scopeOf(arr), idx);
                    stack.push(e);
                    noteDesc(e, elementDesc(arr));       // so a boolean[] element reads as a boolean
                }
                case ArrayStoreInstruction as -> {
                    Expression v = stack.pop(); Expression idx = stack.pop(); Expression arr = stack.pop();
                    addStmt(new ExpressionStmt(new AssignExpr(
                            new ArrayAccessExpr(scopeOf(arr), idx), coerce(v, elementDesc(arr)),
                            AssignExpr.Operator.ASSIGN)));
                }
                case NewPrimitiveArrayInstruction na ->
                        stack.push(newArray1(StaticJavaParser.parseType(primName(na.typeKind())), stack.pop()));
                case NewReferenceArrayInstruction na ->
                        stack.push(newArray1(type(na.componentType().asSymbol()), stack.pop()));
                case NewMultiArrayInstruction na -> {
                    List<Expression> sizes = popArgs(stack, na.dimensions());
                    String d = na.arrayType().asSymbol().descriptorString();
                    while (d.startsWith("[")) d = d.substring(1);
                    NodeList<ArrayCreationLevel> levels = new NodeList<>();
                    for (Expression sz : sizes) levels.add(new ArrayCreationLevel(sz));
                    stack.push(new ArrayCreationExpr(StaticJavaParser.parseType(ClassDesc.ofDescriptor(d).displayName()), levels, null));
                }
                case NewObjectInstruction nw -> {
                    Expression marker = new NameExpr("$new");
                    pendingNew.put(marker, nw.className().asInternalName());
                    stack.push(marker);
                }
                case StackInstruction sk -> {
                    switch (sk.opcode()) {
                        case DUP, DUP_X1, DUP_X2 -> {          // duplicate top-of-stack (optionally deeper)
                            Expression top = stack.peek();
                            int depth = sk.opcode() == Opcode.DUP ? 0 : (sk.opcode() == Opcode.DUP_X1 ? 1 : 2);
                            insertDup(top, depth);
                        }
                        case DUP2, DUP2_X1, DUP2_X2 -> {       // duplicate top one/two values (best effort)
                            Expression top = stack.peek();
                            int depth = sk.opcode() == Opcode.DUP2 ? 0 : (sk.opcode() == Opcode.DUP2_X1 ? 1 : 2);
                            insertDup(top, depth);
                        }
                        case SWAP -> { Expression a = stack.pop(), b = stack.pop(); stack.push(a); stack.push(b); }
                        case POP -> {
                            Expression e = stack.pop();
                            if (e instanceof MethodCallExpr || e instanceof ObjectCreationExpr)
                                addStmt(new ExpressionStmt(e));
                        }
                        case POP2 -> { if (!stack.isEmpty()) stack.pop(); }
                        default -> { markUnsupported(in); addStmt(todo(in.opcode().toString())); }
                    }
                }
                case InvokeDynamicInstruction indy -> stack.push(invokeDynamic(indy));
                case InvokeInstruction inv -> invoke(inv);
                case ReturnInstruction ri -> {
                    // `return true` in a boolean method compiles to `iconst_1; ireturn`, so the value has to
                    // be read back against the DECLARED return type or it prints as `return 1`.
                    if (ri.opcode() != Opcode.RETURN) addStmt(new ReturnStmt(coerce(stack.pop(), retDesc)));
                }
                default -> {
                    if (in.opcode() == Opcode.ATHROW) { addStmt(new ThrowStmt(stack.pop())); }
                    else if (in.opcode() == Opcode.MONITORENTER || in.opcode() == Opcode.MONITOREXIT) {
                        if (!stack.isEmpty()) stack.pop();       // structural; the synchronized wrapper is recovered separately
                    }
                    else { markUnsupported(in); addStmt(todo(in.opcode().toString())); }
                }
            }
        }

        void invoke(InvokeInstruction inv) {
            MethodTypeDesc d = inv.typeSymbol();
            int n = d.parameterCount();
            boolean isStatic = inv.opcode() == Opcode.INVOKESTATIC;
            boolean isInit = inv.opcode() == Opcode.INVOKESPECIAL && inv.name().stringValue().equals("<init>");
            List<Expression> args = popArgs(stack, n);
            for (int i = 0; i < n; i++) args.set(i, coerceArg(args.get(i), d.parameterType(i).descriptorString()));
            if (isInit) {
                Expression recv = stack.isEmpty() ? new NameExpr("?") : stack.pop();
                String g = pendingNew.get(recv);
                if (g == null && inCtor) {
                    // No preceding NEW, so the receiver is `this`: an explicit constructor invocation.
                    // Delegation to this class is `this(args)`; anything else is the superclass. A no-arg
                    // super() call is the prologue javac emits for EVERY constructor, so it is dropped
                    // unless it was written -- which cannot be told apart, and omitting it is equivalent.
                    boolean thisCall = cm != null && inv.owner().asInternalName()
                            .equals(cm.thisClass().asInternalName());
                    if (dropSuper && !thisCall) return;
                    if (thisCall || !args.isEmpty())
                        addStmt(new ExplicitConstructorInvocationStmt(thisCall, null, NodeList.nodeList(args)));
                    return;
                }
                if (g == null) g = inv.owner().asInternalName();
                if (!stack.isEmpty() && stack.peek() == recv) stack.pop();
                // A non-static inner class takes its enclosing instance as a hidden first argument. That
                // cannot be written as an argument in source: the qualified form `outer.new Inner(rest)` is
                // how it is expressed, so the argument becomes the scope instead.
                Expression outer = innerCtorOuter(g, d, args);
                if (outer != null) {
                    dropNullCheckOf(outer);
                    stack.push(new ObjectCreationExpr(scopeOf(outer), ctype(g),
                            NodeList.nodeList(args.subList(1, args.size()))));
                    return;
                }
                stack.push(new ObjectCreationExpr(null, ctype(g), NodeList.nodeList(args)));
                return;
            }
            // invokespecial on an instance method of another class is `super.m()`. Written as `this.m()` it
            // dispatches virtually and finds the override instead, so `super.sound()` called the very method
            // it was reaching past. (invokespecial also covers <init>, handled above, and private methods of
            // this class, which the owner check excludes.)
            boolean superCall = inv.opcode() == Opcode.INVOKESPECIAL && cm != null
                    && !inv.owner().asInternalName().equals(cm.thisClass().asInternalName());
            Expression scope;
            if (isStatic) {
                scope = new NameExpr(simpleRef(inv.owner().asInternalName()));
            } else {
                Expression recv = stack.isEmpty() ? new NameExpr("?") : stack.pop();
                scope = superCall ? new SuperExpr() : scopeOf(recv);
            }
            MethodCallExpr call = new MethodCallExpr(scope, inv.name().stringValue(), NodeList.nodeList(args));
            if (d.returnType().descriptorString().equals("V")) addStmt(new ExpressionStmt(call));
            else { stack.push(call); noteDesc(call, d.returnType().descriptorString()); }
        }

        /**
         * The enclosing-instance argument of a non-static inner class's constructor, or null if this is not
         * one. Recognised structurally: the class is nested ({@code Outer$Inner}) and the constructor's first
         * parameter is exactly its immediately enclosing class.
         */
        Expression innerCtorOuter(String ownerInternal, MethodTypeDesc d, List<Expression> args) {
            int dollar = ownerInternal.lastIndexOf('$');
            if (dollar < 0 || args.isEmpty() || d.parameterCount() == 0) return null;
            String enclosing = ownerInternal.substring(0, dollar);
            return d.parameterType(0).descriptorString().equals("L" + enclosing + ";") ? args.get(0) : null;
        }

        /**
         * Drop the {@code Objects.requireNonNull(q)} javac inserts to null-check the qualifier of
         * {@code q.new Inner()}. It is emitted as a statement just before the allocation, and has no source
         * form, so it is removed once the qualified creation it guards is recovered.
         */
        void dropNullCheckOf(Expression qualifier) {
            if (stmts.isEmpty()) return;
            Statement last = stmts.get(stmts.size() - 1);
            if (last instanceof ExpressionStmt es && es.getExpression() instanceof MethodCallExpr mc
                    && mc.getNameAsString().equals("requireNonNull")
                    && mc.getArguments().size() == 1
                    && mc.getArgument(0).toString().equals(qualifier.toString()))
                stmts.remove(stmts.size() - 1);
        }

        /** invokedynamic: LambdaMetafactory call sites become lambda expressions or method references. */
        Expression invokeDynamic(InvokeDynamicInstruction indy) {
            List<Expression> captures = popArgs(stack, indy.typeSymbol().parameterCount());
            try {
                BootstrapMethodEntry bsm = indy.invokedynamic().bootstrap();
                String bo = bsm.bootstrapMethod().reference().owner().asInternalName();
                if (bo.equals("java/lang/invoke/LambdaMetafactory") && bsm.arguments().size() >= 2
                        && bsm.arguments().get(1) instanceof MethodHandleEntry mh) {
                    var ref = mh.reference();
                    String owner = ref.owner().asInternalName();
                    String name = ref.nameAndType().name().stringValue();
                    String desc = ref.nameAndType().type().stringValue();
                    if (name.startsWith("lambda$") && cm != null) {
                        Expression lam = lambdaFromImpl(name, desc, captures.size());
                        if (lam != null) return lam;
                    }
                    return methodRef(mh.kind(), owner, name, captures);
                }
                if (bo.equals("java/lang/invoke/StringConcatFactory")) {
                    String iname = indy.name().stringValue();
                    if (iname.equals("makeConcatWithConstants") && !bsm.arguments().isEmpty()
                            && bsm.arguments().get(0) instanceof StringEntry recipe)
                        return stringConcat(recipe.stringValue(), captures, bsm.arguments());
                    if (iname.equals("makeConcat"))
                        return stringConcat(null, captures, bsm.arguments());
                }
            } catch (RuntimeException ignore) { }
            markUnsupported(indy);
            return unsupportedExpr("indy_" + indy.name().stringValue());
        }

        Expression methodRef(int kind, String owner, String name, List<Expression> captures) {
            if (kind == 8 || name.equals("<init>"))                       // REF_newInvokeSpecial -> Owner::new
                return new MethodReferenceExpr(new TypeExpr(StaticJavaParser.parseType(simpleRef(owner))), null, "new");
            if ((kind == 5 || kind == 7 || kind == 9) && captures.size() == 1)   // bound instance receiver::name
                return new MethodReferenceExpr(scopeOf(captures.get(0)), null, name);
            return new MethodReferenceExpr(new TypeExpr(StaticJavaParser.parseType(simpleRef(owner))), null, name);
        }

        /** Reconstruct a lambda by decompiling the synthetic lambda$ method; captures are the first nCap params. */
        Expression lambdaFromImpl(String name, String desc, int nCap) {
            MethodModel impl = null;
            for (MethodModel mm : cm.methods())
                if (mm.methodName().stringValue().equals(name) && mm.methodTypeSymbol().descriptorString().equals(desc)) { impl = mm; break; }
            if (impl == null) return null;
            CodeAttribute icode = (CodeAttribute) impl.findAttribute(Attributes.code()).orElse(null);
            if (icode == null) return null;
            Map<Integer,String> iName = new HashMap<>(), iDesc = new HashMap<>();
            List<LocalVariable> ilvs = new ArrayList<>(); List<LocalVariableType> ilvts = new ArrayList<>();
            List<CodeElement> iseq = new ArrayList<>();
            for (CodeElement e : icode) {
                switch (e) {
                    case LocalVariable lv -> { iName.put(lv.slot(), lv.name().stringValue());
                                               iDesc.put(lv.slot(), lv.type().stringValue()); ilvs.add(lv); }
                    case LocalVariableType lvt -> ilvts.add(lvt);
                    case Instruction i -> iseq.add(i);
                    case LabelTarget lt -> iseq.add(lt);
                    default -> { }
                }
            }
            Cfg icfg = null; try { icfg = buildCfg(cm, impl); } catch (RuntimeException ig) { }
            Locals ilocals = Locals.from(ilvs, ilvts, icfg != null ? icfg.labelPos : Map.of());
            MethodTypeDesc imt = impl.methodTypeSymbol();
            boolean iStatic = impl.flags().has(AccessFlag.STATIC);
            Set<String> idecl = new HashSet<>();
            NodeList<Parameter> lambdaParams = new NodeList<>();
            int slot = iStatic ? 0 : 1;
            for (int i = 0; i < imt.parameterCount(); i++) {
                Locals.Scope ps = ilocals.at(slot, 0);
                String pn = ps != null ? ps.name() : iName.getOrDefault(slot, "p" + i);
                idecl.add(ps != null ? slotVarKey(ilocals, slot, 0) : ("v" + slot));
                // [bytecode] Recover the parameter's declared type from the synthetic
                // method's descriptor instead of leaving it unknown: the Laurel
                // generator needs typed quantifier binders (forall((int x) -> ...)).
                if (i >= nCap) lambdaParams.add(new Parameter(type(imt.parameterType(i)), pn));
                String dd = imt.parameterType(i).descriptorString();
                slot += (dd.equals("J") || dd.equals("D")) ? 2 : 1;
            }
            BlockStmt block = buildBody(cm, impl, icode, icfg, ilocals, idecl, iName, iDesc, iseq);
            if (block.getStatements().size() == 1 && block.getStatement(0) instanceof ReturnStmt rs && rs.getExpression().isPresent())
                return new LambdaExpr(lambdaParams, rs.getExpression().get());   // expression lambda
            return new LambdaExpr(lambdaParams, block);
        }

        /**
         * The scope for an instance field access: {@code super} when this class shadows the inherited field
         * being read, otherwise the receiver as written.
         *
         * <p>The fieldref's OWNER is the only thing separating the two. A subclass reading its own {@code x}
         * names itself; reading the inherited one names the superclass. Both come out as {@code this.x}, which
         * resolves to the shadowing field either way -- so {@code this.x - super.x} quietly evaluated to zero.
         */
        Expression superOrScope(FieldInstruction fi, Expression recv) {
            if (cm == null || !"this".equals(recv.toString())) return scopeOf(recv);
            if (fi.owner().asInternalName().equals(cm.thisClass().asInternalName())) return scopeOf(recv);
            String fn = fi.name().stringValue();
            for (FieldModel f : cm.fields())
                if (f.fieldName().stringValue().equals(fn)) return new SuperExpr();
            return scopeOf(recv);        // not shadowed: the plain name already reaches the inherited field
        }

        void field(FieldInstruction fi) {
            String owner = simpleRef(fi.owner().asInternalName());
            String fn = fi.name().stringValue();
            String fdesc = fi.typeSymbol().descriptorString();
            switch (fi.opcode()) {
                case GETSTATIC -> { Expression e = new FieldAccessExpr(new NameExpr(owner), fn); stack.push(e); noteDesc(e, fdesc); }
                case GETFIELD -> { Expression r = stack.isEmpty() ? new NameExpr("?") : stack.pop();
                                   // An inner class holds its enclosing instance in the synthetic this$0
                                   // field; reading it IS the source's `Outer.this`.
                                   if (fn.equals("this$0")) {
                                       Expression e = new ThisExpr(
                                               new com.github.javaparser.ast.expr.Name(
                                                       simpleRef(innerName(fi.typeSymbol()))));
                                       stack.push(e); noteDesc(e, fdesc);
                                   } else {
                                       Expression e = new FieldAccessExpr(superOrScope(fi, r), fn);
                                       stack.push(e); noteDesc(e, fdesc);
                                   } }
                case PUTSTATIC -> { Expression v = coerce(stack.pop(), fi.typeSymbol().descriptorString());
                                    Expression tgt = new FieldAccessExpr(new NameExpr(owner), fn);
                                    if (!asIncrementExprOn(tgt, v))
                                        addStmt(new ExpressionStmt(new AssignExpr(tgt, v, AssignExpr.Operator.ASSIGN))); }
                case PUTFIELD -> { Expression v = coerce(stack.pop(), fi.typeSymbol().descriptorString()); Expression r = stack.pop();
                                   // this$0 is the compiler wiring up an inner class's enclosing instance,
                                   // which is recovered as Outer.this; the assignment has no source form.
                                   if (!fn.equals("this$0")) {
                                       Expression tgt = new FieldAccessExpr(superOrScope(fi, r), fn);
                                       if (!asIncrementExprOn(tgt, v))
                                           addStmt(new ExpressionStmt(new AssignExpr(tgt, v,
                                                   AssignExpr.Operator.ASSIGN)));
                                   } }
                default -> { }
            }
        }

        void operator(OperatorInstruction oi) {
            switch (oi.opcode()) {
                case IADD, LADD -> bin(BinaryExpr.Operator.PLUS);
                case ISUB, LSUB -> bin(BinaryExpr.Operator.MINUS);
                case IMUL, LMUL -> bin(BinaryExpr.Operator.MULTIPLY);
                case IDIV, LDIV -> bin(BinaryExpr.Operator.DIVIDE);
                case IREM, LREM -> bin(BinaryExpr.Operator.REMAINDER);
                case IAND, LAND -> bin(BinaryExpr.Operator.BINARY_AND);
                case IOR,  LOR  -> bin(BinaryExpr.Operator.BINARY_OR);
                case IXOR, LXOR -> bin(BinaryExpr.Operator.XOR);
                case ISHL, LSHL -> bin(BinaryExpr.Operator.LEFT_SHIFT);
                case ISHR, LSHR -> bin(BinaryExpr.Operator.SIGNED_RIGHT_SHIFT);
                case IUSHR, LUSHR -> bin(BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT);
                case INEG, LNEG, FNEG, DNEG -> stack.push(new UnaryExpr(scopeOf(stack.pop()), UnaryExpr.Operator.MINUS));
                case FADD, DADD -> bin(BinaryExpr.Operator.PLUS);
                case FSUB, DSUB -> bin(BinaryExpr.Operator.MINUS);
                case FMUL, DMUL -> bin(BinaryExpr.Operator.MULTIPLY);
                case FDIV, DDIV -> bin(BinaryExpr.Operator.DIVIDE);
                case FREM, DREM -> bin(BinaryExpr.Operator.REMAINDER);
                case LCMP  -> push3Way("Long", null);
                case FCMPL -> push3Way("Float", Boolean.FALSE);
                case FCMPG -> push3Way("Float", Boolean.TRUE);
                case DCMPL -> push3Way("Double", Boolean.FALSE);
                case DCMPG -> push3Way("Double", Boolean.TRUE);
                case ARRAYLENGTH -> stack.push(new FieldAccessExpr(scopeOf(stack.pop()), "length"));
                default -> { markUnsupported(oi); stack.push(unsupportedExpr("op_" + oi.opcode())); }
            }
        }
        void bin(BinaryExpr.Operator op) { Expression b = stack.pop(), a = stack.pop(); stack.push(binexpr(a, op, b)); }

        /**
         * The long/float/double comparisons ({@code lcmp}, {@code fcmpl/g}, {@code dcmpl/g}) have no direct
         * source form: they push -1/0/1 and are then tested against 0 by a following {@code if<cond>}, so
         * {@code lcmp; iflt} is really {@code a < b}. The pair of operands is remembered against the pushed
         * value so {@link #cmpLit} / {@link #unaryBool} can rebuild the relational operator when the branch
         * consumes it.
         *
         * <p>The value pushed is a real {@code Long/Float/Double.compare(a, b)} call rather than an opaque
         * marker, so the rare case of a 3-way result that is NOT consumed by a comparison still emits valid,
         * correctly-typed Java instead of a placeholder comment.
         */
        void push3Way(String boxType, Boolean nanHigh) {
            Expression b = stack.pop(), a = stack.pop();
            Expression e = new MethodCallExpr(new NameExpr(boxType), "compare", NodeList.nodeList(a, b));
            cmp3.put(e, new Expression[]{ a, b });
            if (nanHigh != null) cmp3NanHigh.put(e, nanHigh);   // absent for lcmp: long IS totally ordered
            stack.push(e);
        }

        /**
         * Rebuild a relational test from a 3-way comparison, keeping NaN on the correct side.
         *
         * <p>{@code op} is the FALL condition of the branch that consumed the comparison, already inverted
         * from the branch test. For an ordered type that inversion is the whole story, but floating point has
         * no total order: every one of {@code < <= > >=} is false when an operand is NaN, so the fall path of
         * a float test can be a set no single operator describes.
         *
         * <p>Which side NaN lands on is exactly what {@code dcmpl} vs {@code dcmpg} encodes -- they push -1
         * and +1 for NaN respectively -- so the pair (variant, branch test) says whether the fall path takes
         * NaN. When it does, the condition has to be written as the negation of the strict test, which is how
         * the source must have written it too: `x >= 0.0` and `!(x < 0.0)` differ only on NaN, and javac picks
         * the cmp variant accordingly.
         */
        Expression relational(Expression src, Expression a, BinaryExpr.Operator op, Expression b) {
            Boolean nanHigh = cmp3NanHigh.get(src);
            if (nanHigh != null) {
                boolean high = op == BinaryExpr.Operator.GREATER_EQUALS || op == BinaryExpr.Operator.GREATER;
                if (high == nanHigh) {                      // the fall path includes NaN
                    BinaryExpr.Operator strict = switch (op) {
                        case GREATER_EQUALS -> BinaryExpr.Operator.LESS;
                        case GREATER -> BinaryExpr.Operator.LESS_EQUALS;
                        case LESS -> BinaryExpr.Operator.GREATER_EQUALS;
                        case LESS_EQUALS -> BinaryExpr.Operator.GREATER;
                        default -> null;
                    };
                    if (strict != null) {
                        Expression inner = binexpr(a, strict, b);
                        if (inner instanceof BinaryExpr ib) ib.setData(UNORDERED, true);
                        return new UnaryExpr(par(inner), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
                    }
                }
            }
            Expression e = binexpr(a, op, b);
            if (nanHigh != null && e instanceof BinaryExpr be) be.setData(UNORDERED, true);
            return e;
        }

        /** The operands behind a 3-way comparison result, or null if {@code e} is not one. */
        Expression[] threeWay(Expression e) { return cmp3.get(e); }

        // ---- boolean-materialization peephole (if_icmp<c>/if<c> ; iconst_1 ; goto ; iconst_0) ----

        int tryBooleanRegion(List<CodeElement> seq, int k, BranchInstruction first) {
            Label lfalse = first.target();
            int p = -1;
            for (int j = k; j + 4 < seq.size(); j++) {
                if (isIntConst(seq.get(j), 1)
                        && seq.get(j + 1) instanceof BranchInstruction g && g.opcode() == Opcode.GOTO
                        && seq.get(j + 2) instanceof LabelTarget f && sameLabel(f.label(), lfalse)
                        && isIntConst(seq.get(j + 3), 0)
                        && seq.get(j + 4) instanceof LabelTarget e && sameLabel(e.label(), g.target())) {
                    p = j; break;
                }
                CodeElement el = seq.get(j);
                if (el instanceof BranchInstruction b) {
                    if (!isConditionalBranch(b.opcode()) || !sameLabel(b.target(), lfalse)) return 0;
                } else if (el instanceof LabelTarget) {
                    return 0;
                } else if (!(el instanceof ConstantInstruction || el instanceof LoadInstruction
                        || el instanceof OperatorInstruction || el instanceof ConvertInstruction
                        || el instanceof FieldInstruction || el instanceof InvokeInstruction
                        || el instanceof ArrayLoadInstruction)) {
                    return 0;
                }
            }
            if (p < 0) return 0;

            List<Expression> guards = new ArrayList<>();
            for (int j = k; j < p; j++) {
                CodeElement el = seq.get(j);
                if (el instanceof BranchInstruction b && isConditionalBranch(b.opcode()))
                    guards.add(materializeComparison(b.opcode()));
                else
                    translate((Instruction) el);
            }
            if (guards.isEmpty()) return 0;

            Expression result = guards.get(0);
            for (int i = 1; i < guards.size(); i++)
                result = binexpr(result, BinaryExpr.Operator.AND, guards.get(i));
            bools.add(result);
            stack.push(result);
            return (p + 5) - k;
        }

        /** The materialized boolean is the NEGATION of the branch condition (the branch jumps to false). */
        Expression materializeComparison(Opcode op) {
            return switch (op) {
                case IF_ICMPEQ, IF_ACMPEQ -> cmp(BinaryExpr.Operator.NOT_EQUALS);
                case IF_ICMPNE, IF_ACMPNE -> cmp(BinaryExpr.Operator.EQUALS);
                case IF_ICMPLT -> cmp(BinaryExpr.Operator.GREATER_EQUALS);
                case IF_ICMPGE -> cmp(BinaryExpr.Operator.LESS);
                case IF_ICMPGT -> cmp(BinaryExpr.Operator.LESS_EQUALS);
                case IF_ICMPLE -> cmp(BinaryExpr.Operator.GREATER);
                case IFNULL    -> cmpLit(new NullLiteralExpr(), BinaryExpr.Operator.NOT_EQUALS);
                case IFNONNULL -> cmpLit(new NullLiteralExpr(), BinaryExpr.Operator.EQUALS);
                case IFLT -> cmpLit(new IntegerLiteralExpr("0"), BinaryExpr.Operator.GREATER_EQUALS);
                case IFGE -> cmpLit(new IntegerLiteralExpr("0"), BinaryExpr.Operator.LESS);
                case IFGT -> cmpLit(new IntegerLiteralExpr("0"), BinaryExpr.Operator.LESS_EQUALS);
                case IFLE -> cmpLit(new IntegerLiteralExpr("0"), BinaryExpr.Operator.GREATER);
                case IFEQ -> unaryBool(true);
                case IFNE -> unaryBool(false);
                default -> new NameExpr("/*?" + op + "*/");
            };
        }
        boolean isBool(Expression e) { return bools.contains(e) || isBooleanish(e); }
        Expression cmp(BinaryExpr.Operator op) {
            Expression b = stack.pop(), a = stack.pop();
            if ((op == BinaryExpr.Operator.EQUALS || op == BinaryExpr.Operator.NOT_EQUALS) && (isBool(a) || isBool(b))) {
                a = coerce(a, "Z"); b = coerce(b, "Z");   // boolean comparison: 0/1 -> false/true
            }
            return binexpr(a, op, b);
        }
        Expression cmpLit(Expression lit, BinaryExpr.Operator op) {
            Expression a = stack.pop();
            // `<3-way result> <op> 0` is just `left <op> right` on the original operands
            Expression[] pair = threeWay(a);
            if (pair != null && isZeroLit(lit)) return relational(a, pair[0], op, pair[1]);
            return binexpr(a, op, lit);
        }
        Expression unaryBool(boolean trueWhenNonZero) {
            Expression a = stack.pop();
            BinaryExpr.Operator op = trueWhenNonZero ? BinaryExpr.Operator.NOT_EQUALS : BinaryExpr.Operator.EQUALS;
            Expression[] pair = threeWay(a);
            if (pair != null) return relational(a, pair[0], op, pair[1]);   // lcmp;ifeq  ->  a != b
            if (isBool(a))
                return trueWhenNonZero ? a : new UnaryExpr(par(a), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
            return binexpr(a, op, new IntegerLiteralExpr("0"));
        }
        static boolean isZeroLit(Expression e) {
            return e instanceof IntegerLiteralExpr il && il.getValue().equals("0");
        }
        boolean isBooleanish(Expression e) {
            if (e instanceof InstanceOfExpr || e instanceof BooleanLiteralExpr) return true;
            if (e instanceof UnaryExpr u) return u.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT;
            if (e instanceof EnclosedExpr en) return isBooleanish(en.getInner());
            if (e instanceof BinaryExpr b) return switch (b.getOperator()) {
                case EQUALS, NOT_EQUALS, LESS, GREATER, LESS_EQUALS, GREATER_EQUALS, AND, OR -> true;
                case BINARY_AND, BINARY_OR, XOR -> isBool(b.getLeft()) && isBool(b.getRight());  // bool bitwise
                default -> false;
            };
            return false;
        }
    }

    // ---- shared static helpers ----

    static Expression par(Expression e) {
        return (e instanceof BinaryExpr || e instanceof ConditionalExpr || e instanceof AssignExpr
                || e instanceof CastExpr || e instanceof InstanceOfExpr) ? new EnclosedExpr(e) : e;
    }

    /** Java binary-operator precedence rank (1 = binds tightest). */
    static int prec(BinaryExpr.Operator op) {
        return switch (op) {
            case MULTIPLY, DIVIDE, REMAINDER -> 1;
            case PLUS, MINUS -> 2;
            case LEFT_SHIFT, SIGNED_RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT -> 3;
            case LESS, GREATER, LESS_EQUALS, GREATER_EQUALS -> 4;
            case EQUALS, NOT_EQUALS -> 5;
            case BINARY_AND -> 6;
            case XOR -> 7;
            case BINARY_OR -> 8;
            case AND -> 9;
            case OR -> 10;
        };
    }
    /**
     * Build {@code left op right}, parenthesizing operands only where needed to preserve the parse
     * (JavaParser prints the AST verbatim without inserting precedence parentheses).
     */
    static Expression binexpr(Expression left, BinaryExpr.Operator op, Expression right) {
        return new BinaryExpr(wrapOperand(left, op, false), wrapOperand(right, op, true), op);
    }
    static Expression wrapOperand(Expression child, BinaryExpr.Operator parent, boolean rightSide) {
        if (child instanceof EnclosedExpr) return child;
        if (child instanceof ConditionalExpr || child instanceof AssignExpr || child instanceof InstanceOfExpr)
            return new EnclosedExpr(child);
        if (child instanceof BinaryExpr b) {
            int cp = prec(b.getOperator()), pp = prec(parent);
            if (cp > pp || (rightSide && cp == pp)) return new EnclosedExpr(child);
        }
        return child;
    }
    static Expression scopeOf(Expression e) {
        if (e instanceof CastExpr || e instanceof InstanceOfExpr || e instanceof BinaryExpr
                || e instanceof UnaryExpr || e instanceof ConditionalExpr
                || e instanceof AssignExpr || e instanceof ArrayCreationExpr)
            return new EnclosedExpr(e);
        return e;
    }
    /**
     * A single-dimension allocation {@code new elem[count]}. When {@code elem} is ITSELF an array (from
     * {@code anewarray [I}, i.e. source {@code new int[count][]}), its dimensions must become trailing
     * empty levels: the sized level comes first, so the element type is peeled down to its base.
     * Otherwise the printed form would be {@code new int[][count]}, which does not parse.
     */
    static Expression newArray1(Type elem, Expression count) {
        NodeList<ArrayCreationLevel> levels = new NodeList<>(new ArrayCreationLevel(count));
        Type base = elem;
        while (base instanceof ArrayType at) { levels.add(new ArrayCreationLevel()); base = at.getComponentType(); }
        return new ArrayCreationExpr(base, levels, null);
    }
    static String primName(TypeKind tk) {
        return switch (tk) {
            case BOOLEAN -> "boolean"; case BYTE -> "byte"; case CHAR -> "char"; case SHORT -> "short";
            case INT -> "int"; case LONG -> "long"; case FLOAT -> "float"; case DOUBLE -> "double";
            default -> "Object";
        };
    }
    static List<Expression> popArgs(Deque<Expression> stack, int n) {
        List<Expression> a = new ArrayList<>(Collections.nCopies(n, null));
        for (int i = n - 1; i >= 0; i--) a.set(i, stack.isEmpty() ? new NameExpr("?") : stack.pop());
        return a;
    }
    static String convTarget(Opcode op) {
        return switch (op.name().charAt(2)) {
            case 'L' -> "long"; case 'I' -> "int"; case 'F' -> "float"; case 'D' -> "double";
            case 'B' -> "byte"; case 'C' -> "char"; case 'S' -> "short"; default -> "int";
        };
    }
    /** The JVM descriptor a conversion opcode produces, so the converted value carries its new type. */
    static String convDesc(Opcode op) {
        return switch (op.name().charAt(2)) {
            case 'L' -> "J"; case 'I' -> "I"; case 'F' -> "F"; case 'D' -> "D";
            case 'B' -> "B"; case 'C' -> "C"; case 'S' -> "S"; default -> "I";
        };
    }
    static ClassOrInterfaceType ctype(String internal) {
        try { return StaticJavaParser.parseClassOrInterfaceType(simpleRef(internal)); }
        catch (Exception e) { return StaticJavaParser.parseClassOrInterfaceType("Object"); }
    }
    static Expression constant(ConstantInstruction ci) {
        if (ci.opcode() == Opcode.ACONST_NULL) return new NullLiteralExpr();
        Object v = ci.constantValue();
        if (v instanceof Integer i) {
            if (i == Integer.MIN_VALUE) return new NameExpr(String.valueOf(i));
            return signed(i < 0, new IntegerLiteralExpr(String.valueOf(Math.abs((long) i))));
        }
        if (v instanceof Long l) {
            if (l == Long.MIN_VALUE) return new NameExpr(l + "L");
            return signed(l < 0, new LongLiteralExpr(Math.abs(l) + "L"));
        }
        if (v instanceof Float f)  return floatLit(f);
        if (v instanceof Double dd) return doubleLit(dd);
        if (v instanceof String s)  return strLit(s);
        if (v instanceof ClassDesc cd) return new ClassExpr(type(cd));
        return new NameExpr("/*const:" + v + "*/");
    }
    static Expression signed(boolean neg, Expression mag) {
        return neg ? new UnaryExpr(mag, UnaryExpr.Operator.MINUS) : mag;
    }

    /**
     * Floating-point literals. Infinity and NaN have no literal syntax in Java -- {@code Double.toString}
     * renders them as {@code Infinity}/{@code NaN}, which would emit an undefined identifier -- so they are
     * written as the corresponding wrapper constants.
     */
    static Expression doubleLit(double d) {
        if (Double.isNaN(d)) return new FieldAccessExpr(new NameExpr("Double"), "NaN");
        if (Double.isInfinite(d))
            return new FieldAccessExpr(new NameExpr("Double"), d > 0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
        // the sign BIT, not `d < 0`, which is false for -0.0 and so dropped the sign: 1.0 / -0.0 is
        // negative infinity where 1.0 / 0.0 is positive
        return signed(Double.doubleToRawLongBits(d) < 0, new DoubleLiteralExpr(String.valueOf(Math.abs(d))));
    }
    static Expression floatLit(float f) {
        if (Float.isNaN(f)) return new FieldAccessExpr(new NameExpr("Float"), "NaN");
        if (Float.isInfinite(f))
            return new FieldAccessExpr(new NameExpr("Float"), f > 0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
        return signed(Float.floatToRawIntBits(f) < 0, new DoubleLiteralExpr(Math.abs(f) + "f"));
    }

    /**
     * A string literal with its contents escaped. JavaParser stores the literal body verbatim, so a raw
     * constant-pool string containing a quote, backslash, or control character would emit source that does
     * not parse.
     */
    static StringLiteralExpr strLit(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"'  -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default   -> {
                    if (c < 0x20 || c == 0x7f) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        StringLiteralExpr e = new StringLiteralExpr();
        e.setValue(b.toString());
        return e;
    }

    /**
     * Reconstruct a StringConcatFactory call site as a chain of {@code +} concatenations.
     * For {@code makeConcatWithConstants} the recipe drives interleaving: {@code \u0001} pulls the next
     * dynamic argument, {@code \u0002} the next bootstrap constant, any other char is literal text.
     * {@code makeConcat} (recipe == null) just concatenates all args in order.
     */
    static Expression stringConcat(String recipe, List<Expression> args, List<LoadableConstantEntry> bsmArgs) {
        List<Expression> parts = new ArrayList<>();
        if (recipe == null) {
            parts.addAll(args);
        } else {
            int ai = 0, ci = 1;                 // ci indexes bsmArgs after the recipe at [0]
            StringBuilder lit = new StringBuilder();
            for (int i = 0; i < recipe.length(); i++) {
                char c = recipe.charAt(i);
                if (c == '\u0001') {            // TAG_ARG
                    if (lit.length() > 0) { parts.add(strLit(lit.toString())); lit.setLength(0); }
                    parts.add(ai < args.size() ? args.get(ai++) : new NameExpr("?"));
                } else if (c == '\u0002') {     // TAG_CONST
                    if (lit.length() > 0) { parts.add(strLit(lit.toString())); lit.setLength(0); }
                    parts.add(ci < bsmArgs.size() ? constEntry(bsmArgs.get(ci++)) : new NameExpr("?"));
                } else {
                    lit.append(c);
                }
            }
            if (lit.length() > 0) parts.add(strLit(lit.toString()));
        }
        if (parts.isEmpty()) return strLit("");
        // Guarantee String semantics: lead with "" unless the first part is already a String literal.
        if (!(parts.get(0) instanceof StringLiteralExpr)) parts.add(0, strLit(""));
        Expression e = parts.get(0);
        for (int i = 1; i < parts.size(); i++) e = binexpr(e, BinaryExpr.Operator.PLUS, parts.get(i));
        return e;
    }

    /** Render a bootstrap-argument constant-pool entry as a literal expression. */
    static Expression constEntry(LoadableConstantEntry e) {
        if (e instanceof StringEntry se)  return strLit(se.stringValue());
        if (e instanceof IntegerEntry ie) { int v = ie.intValue();
            return v == Integer.MIN_VALUE ? new NameExpr(String.valueOf(v))
                    : signed(v < 0, new IntegerLiteralExpr(String.valueOf(Math.abs((long) v)))); }
        if (e instanceof LongEntry le)    { long v = le.longValue();
            return v == Long.MIN_VALUE ? new NameExpr(v + "L")
                    : signed(v < 0, new LongLiteralExpr(Math.abs(v) + "L")); }
        if (e instanceof FloatEntry fe)   return floatLit(fe.floatValue());
        if (e instanceof DoubleEntry de)  return doubleLit(de.doubleValue());
        return new NameExpr("/*const*/");
    }
    /**
     * Declaration-identity key for a local: (slot, name, type). One source variable can have several
     * disjoint LVT liveness scopes (e.g. assigned in each switch case) that share name+type -> same key,
     * so it is declared once. Reused slots with different name/type get different keys.
     */
    static String slotVarKey(Locals locals, int slot, int pos) {
        if (locals != null) {
            Locals.Scope s = locals.at(slot, pos);
            if (s != null) return slot + ":" + s.name() + ":" + s.desc();
            if (locals.bySlot.containsKey(slot)) return "v" + slot;   // unnamed here (no LVT entry)
        }
        return "v" + slot;
    }

    /** Map an arithmetic/bitwise binary opcode to the matching compound-assignment operator. */
    static AssignExpr.Operator assignOp(Opcode op) {
        return switch (op) {
            case IADD, LADD, FADD, DADD -> AssignExpr.Operator.PLUS;
            case ISUB, LSUB, FSUB, DSUB -> AssignExpr.Operator.MINUS;
            case IMUL, LMUL, FMUL, DMUL -> AssignExpr.Operator.MULTIPLY;
            case IDIV, LDIV, FDIV, DDIV -> AssignExpr.Operator.DIVIDE;
            case IREM, LREM, FREM, DREM -> AssignExpr.Operator.REMAINDER;
            case ISHL, LSHL -> AssignExpr.Operator.LEFT_SHIFT;
            case ISHR, LSHR -> AssignExpr.Operator.SIGNED_RIGHT_SHIFT;
            case IUSHR, LUSHR -> AssignExpr.Operator.UNSIGNED_RIGHT_SHIFT;
            case IAND, LAND -> AssignExpr.Operator.BINARY_AND;
            case IOR, LOR -> AssignExpr.Operator.BINARY_OR;
            case IXOR, LXOR -> AssignExpr.Operator.XOR;
            default -> null;
        };
    }
    /** When the target type is boolean, render an int 0/1 literal as false/true (javac erases boolean to int). */
    static Expression coerce(Expression v, String desc) {
        if ("Z".equals(desc) && v instanceof IntegerLiteralExpr il) {
            if (il.getValue().equals("0")) return new BooleanLiteralExpr(false);
            if (il.getValue().equals("1")) return new BooleanLiteralExpr(true);
        }
        return v;
    }

    /** The internal name inside a class descriptor {@code Lpkg/Outer$Inner;}. */
    static String innerName(ClassDesc d) {
        String s = d.descriptorString();
        return s.startsWith("L") ? s.substring(1, s.length() - 1) : s;
    }

    /** True for the integral descriptors a recovered boolean could have been erased into. */
    static boolean isIntLike(String d) {
        return d.equals("I") || d.equals("B") || d.equals("C") || d.equals("S") || d.equals("J");
    }

    static boolean isConditionalBranch(Opcode op) {
        return switch (op) {
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> true;
            default -> false;
        };
    }
    static boolean isIntConst(CodeElement e, int want) {
        return e instanceof ConstantInstruction ci && ci.constantValue() instanceof Integer i && i == want;
    }
    static boolean sameLabel(Label a, Label b) { return a == b || (a != null && a.equals(b)); }

    static void markUnsupported(Instruction in) {
        if (sweeping) { unsupportedHist.merge(in.opcode().name(), 1, Integer::sum); classUnsupported++;
                        noteExample(in.opcode().name()); }
    }
    static Statement todo(String msg) {
        EmptyStmt s = new EmptyStmt();
        s.setComment(new LineComment(" TODO unsupported: " + msg));
        return s;
    }

    /**
     * Placeholder for a value the translator cannot reconstruct. It has to be a legal EXPRESSION rather
     * than a bare comment: a comment in an expression position makes the whole file unparseable, which
     * would hide every other diagnostic in it (javac stops before attribution on a parse error). As an
     * undefined identifier it still fails to compile, so the gap stays visible where it belongs -- in the
     * unsupported-instruction histogram and as a resolution error, not as malformed syntax.
     */
    static Expression unsupportedExpr(String tag) {
        return new NameExpr("UNSUPPORTED_" + tag.replaceAll("[^A-Za-z0-9_]", "_"));
    }
    static Span toSpan(CharacterRangeInfo e) {
        int s = e.characterRangeStart(), t = e.characterRangeEnd();
        return new Span(s >>> 10, s & 1023, t >>> 10, t & 1023);
    }
    static String render(CompilationUnit cu) { return render(cu.toString()); }

    /** Fold {@code @SR} marker comments into trailing {@code // src line:col-line:col} annotations. */
    static String render(String src) {
        List<String> out = new ArrayList<>();
        String pending = null;
        for (String line : src.split("\n", -1)) {
            String tr = line.strip();
            if (tr.startsWith("//")) {
                String c = tr.substring(2).strip();
                if (c.startsWith("@SR ")) { pending = c.substring(4).trim(); continue; }
            }
            if (pending != null && !tr.isEmpty()) { out.add(line + "   // src " + pending); pending = null; }
            else out.add(line);
        }
        return String.join("\n", out);
    }
    static Modifier.Keyword[] classModifiers(ClassModel cm) {
        List<Modifier.Keyword> ks = new ArrayList<>();
        var f = cm.flags();
        if (f.has(AccessFlag.PUBLIC)) ks.add(Modifier.Keyword.PUBLIC);
        if (f.has(AccessFlag.ABSTRACT)) ks.add(Modifier.Keyword.ABSTRACT);
        else if (f.has(AccessFlag.FINAL)) ks.add(Modifier.Keyword.FINAL);
        return ks.toArray(Modifier.Keyword[]::new);
    }
    /**
     * Method modifiers. An interface method that is neither {@code static} nor {@code abstract} is a
     * {@code default} method: the classfile records that only by the ABSENCE of ACC_ABSTRACT, so without
     * adding the keyword back the emitted body sits on a method javac still considers abstract.
     */
    static Modifier.Keyword[] methodModifiers(MethodModel m, boolean ownerIsInterface) {
        List<Modifier.Keyword> ks = new ArrayList<>();
        var f = m.flags();
        if (f.has(AccessFlag.PUBLIC)) ks.add(Modifier.Keyword.PUBLIC);
        else if (f.has(AccessFlag.PRIVATE)) ks.add(Modifier.Keyword.PRIVATE);
        else if (f.has(AccessFlag.PROTECTED)) ks.add(Modifier.Keyword.PROTECTED);
        if (f.has(AccessFlag.STATIC)) ks.add(Modifier.Keyword.STATIC);
        if (f.has(AccessFlag.FINAL)) ks.add(Modifier.Keyword.FINAL);
        // A synchronized method locks implicitly, from this flag alone -- there is no monitorenter in its
        // body to recover it from. Dropping the modifier leaves code that still computes the same answer on
        // one thread, which is exactly why the behaviour suite cannot see the difference.
        if (f.has(AccessFlag.SYNCHRONIZED)) ks.add(Modifier.Keyword.SYNCHRONIZED);
        if (f.has(AccessFlag.ABSTRACT)) ks.add(Modifier.Keyword.ABSTRACT);
        // a PRIVATE interface method is concrete but not `default` -- the two modifiers cannot combine
        else if (ownerIsInterface && !f.has(AccessFlag.STATIC) && !f.has(AccessFlag.PRIVATE))
            ks.add(Modifier.Keyword.DEFAULT);
        return ks.toArray(Modifier.Keyword[]::new);
    }
    // ---- generics: render JDK Signature models into Java type strings (fall back to erased on failure) ----

    /** Render a generic type signature into a Java type string (simple names). */
    static String renderSig(Signature s) {
        return switch (s) {
            case Signature.BaseTypeSig b -> ClassDesc.ofDescriptor(String.valueOf(b.baseType())).displayName();
            case Signature.TypeVarSig tv -> tv.identifier();
            case Signature.ArrayTypeSig a -> renderSig(a.componentSignature()) + "[]";
            case Signature.ClassTypeSig c -> {
                String internal = c.className();
                String n = javaName(internal.substring(internal.lastIndexOf('/') + 1));
                imports.note(internal, n);
                if (c.typeArgs().isEmpty()) yield n;
                List<String> as = new ArrayList<>();
                for (Signature.TypeArg ta : c.typeArgs()) as.add(renderArg(ta));
                yield n + "<" + String.join(", ", as) + ">";
            }
            default -> s.signatureString();
        };
    }
    static String renderArg(Signature.TypeArg ta) {
        return switch (ta) {
            case Signature.TypeArg.Unbounded u -> "?";
            case Signature.TypeArg.Bounded b -> switch (b.wildcardIndicator()) {
                case NONE -> renderSig(b.boundType());
                case EXTENDS -> "? extends " + renderSig(b.boundType());
                case SUPER -> "? super " + renderSig(b.boundType());
            };
        };
    }
    static Type sigType(Signature s) {
        try { return StaticJavaParser.parseType(renderSig(s)); } catch (RuntimeException e) { return null; }
    }
    /** Render generic method type parameters (e.g. {@code <T extends Comparable<T>>}). */
    static NodeList<TypeParameter> typeParams(List<Signature.TypeParam> tps) {
        NodeList<TypeParameter> out = new NodeList<>();
        for (Signature.TypeParam tp : tps) {
            NodeList<ClassOrInterfaceType> bounds = new NodeList<>();
            tp.classBound().ifPresent(cb -> {
                String r = renderSig(cb);
                if (!r.equals("Object")) bounds.add(StaticJavaParser.parseClassOrInterfaceType(r));
            });
            for (Signature.RefTypeSig ib : tp.interfaceBounds())
                bounds.add(StaticJavaParser.parseClassOrInterfaceType(renderSig(ib)));
            out.add(new TypeParameter(tp.identifier(), bounds));
        }
        return out;
    }

    static Type type(ClassDesc d) {
        String printed = javaName(d.displayName());
        noteType(d, printed);
        return StaticJavaParser.parseType(printed);
    }
    static Type type(String desc) {
        return desc == null ? StaticJavaParser.parseType("var") : type(ClassDesc.ofDescriptor(desc));
    }
    /**
     * Render a nested class name for use in source: {@code Outer$Inner} becomes {@code Outer.Inner}.
     *
     * <p>A NUMBERED segment ({@code Outer$1}, {@code Outer$1Local}) is anonymous or local and has no
     * qualified source name -- it cannot be written as {@code Outer.1Local} -- so the qualifier is dropped
     * and only the recovered simple name is used, matching what {@link #simple} declares it as.
     */
    static String javaName(String displayName) {
        String[] segs = displayName.split("\\$", -1);
        StringBuilder b = new StringBuilder(segs[0]);
        for (int i = 1; i < segs.length; i++) {
            String s = segs[i];
            if (s.isEmpty()) continue;
            if (Character.isDigit(s.charAt(0))) {
                int d = 0;
                while (d < s.length() && Character.isDigit(s.charAt(d))) d++;
                String nm = s.substring(d);
                b.setLength(0);
                b.append(nm.isEmpty() ? "Anon" + s.substring(0, d) : nm);
            } else {
                b.append('.').append(s);
            }
        }
        return b.toString();
    }
    static String name(Map<Integer,String> names, int slot) { return names.getOrDefault(slot, "v" + slot); }
    /**
     * The innermost segment of an internal name, as a legal Java identifier.
     *
     * <p>The JVM numbers the nested classes that have no usable source name: {@code Outer$1} is anonymous,
     * while {@code Outer$1Local} is a LOCAL class whose real name follows the disambiguating digits. Taking
     * the segment verbatim yields {@code 1} or {@code 1Local}, neither of which parses (and {@code 1E} is
     * even read as a malformed float), so the digits are stripped to recover the local name and an
     * anonymous class is given a synthetic one.
     */
    static String simple(String internal) {
        int i = Math.max(internal.lastIndexOf('/'), internal.lastIndexOf('$'));
        String seg = internal.substring(i + 1);
        if (seg.isEmpty() || !Character.isDigit(seg.charAt(0))) return seg;
        int d = 0;
        while (d < seg.length() && Character.isDigit(seg.charAt(d))) d++;
        String name = seg.substring(d);
        return name.isEmpty() ? "Anon" + seg.substring(0, d)     // Outer$1      -> Anon1
                              : name;                            // Outer$1Local -> Local
    }
}
