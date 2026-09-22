package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.SourceFile;
import org.strata.java.bytecode.frontend.BytecodeFrontend;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JVM bytecode-verification gate: with the gate enabled (as the
 * constructors used here do by default), every classfile entering
 * {@link BytecodeToLaurelCompiler#analyze} must pass {@link ClassFile#verify}
 * or the whole input is refused ({@link RefusedInputException}, exit 2 at the
 * driver) with the verifier's own message.
 *
 * <p>The unverifiable inputs are GENERATED with the ClassFile API: javac
 * cannot produce them (emitting verifiable bytecode is a compiler correctness
 * contract), and handcrafted classfiles keep each failure deterministic and
 * minimal -- one class, one method, minimal instructions, exactly ONE
 * VerifyError. The corruption tests exercise a spread of verifier concerns
 * (return typing, operand-stack typing, control-flow completeness, stack-map
 * presence, operand-stack depth, operand-stack slot sizes, local-store
 * typing, uninitialized-object tracking, and exception-table checking through
 * the hierarchy resolver);
 * four of them land on the verifier's shared "Bad type on operand stack"
 * message, so the assertions pin that text rather than claiming to
 * distinguish the underlying rule.
 *
 * <p>Corruptions the builder's stack-map generator would reject at build
 * time are emitted with {@link ClassFile.StackMapsOption#DROP_STACK_MAPS},
 * which disables that build-time type checking entirely -- the CodeBuilder
 * itself emits whatever opcodes it is asked for.
 */
class JvmVerificationGateTest {

    /**
     * Return-type check. Method declared {@code ()I} but its body is a bare
     * void return -- no int is on the stack to return.
     * <pre>
     *   static int bad() {
     *       RETURN           // void return in an int method
     *   }
     * </pre>
     */
    @Test
    void voidReturnInIntMethod(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Broken"), cb -> cb
                .withMethodBody("bad", MethodTypeDesc.ofDescriptor("()I"),
                        ClassFile.ACC_STATIC, code -> code.return_()));
        assertRefused(tmp, bytes, "Method expects a return value");
    }

    /**
     * Operand-stack type check. An int is returned through {@code areturn},
     * the REFERENCE-return opcode (the {@code a} prefix means reference).
     * <pre>
     *   static Object bad() {
     *       ICONST_0         // push int 0
     *       ARETURN          // return it as a reference  <-- int is not a reference
     *   }
     * </pre>
     */
    @Test
    void intReturnedAsReference(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("IntAsRef"), cb -> cb
                .withMethodBody("bad", MethodTypeDesc.ofDescriptor("()Ljava/lang/Object;"),
                        ClassFile.ACC_STATIC, code -> code.iconst_0().areturn()));
        assertRefused(tmp, bytes, "Bad type on operand stack");
    }

    /**
     * Operand-stack slot sizes. A {@code long} takes two stack slots and
     * {@code pop} removes exactly one, so popping a long is illegal (that
     * takes {@code pop2}). The translation's operand-stack simulation tracks
     * values, not slots: to it {@code lconst_1; pop} cancels out, the defect
     * disappears, and what remains is {@code check(true)}, which Strata
     * verifies. So without the gate this bytecode, which no JVM will load,
     * gets a clean "verified" verdict. Only constructible with
     * DROP_STACK_MAPS (the default builder rejects it at build time).
     * <pre>
     *   static void f() {
     *       LCONST_1                          // push long 1 (two slots)
     *       POP                               // pop one slot  <-- half a long
     *       ICONST_1                          // push true
     *       INVOKESTATIC JVerify.check(Z)V    // check(true)
     *       RETURN
     *   }
     * </pre>
     */
    @Test
    void popOnLongIsRefused(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                .build(ClassDesc.of("PopLong"), cb -> cb
                        .withMethodBody("f", MethodTypeDesc.ofDescriptor("()V"),
                                ClassFile.ACC_STATIC, code -> code
                                        .lconst_1()
                                        .pop()
                                        .iconst_1()
                                        .invokestatic(ClassDesc.of("org.strata.jverify.JVerify"),
                                                "check", MethodTypeDesc.ofDescriptor("(Z)V"))
                                        .return_()));
        assertRefused(tmp, bytes, "Bad type on operand stack");
    }

    /**
     * Control-flow completeness. The only instruction is a {@code nop}: with
     * no terminating return/throw, execution runs off the end of the code.
     * <pre>
     *   static void bad() {
     *       NOP              // ...then falls off the end (no RETURN)
     *   }
     * </pre>
     */
    @Test
    void fallsOffCodeEnd(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("FallsOff"), cb -> cb
                .withMethodBody("bad", MethodTypeDesc.ofDescriptor("()V"),
                        ClassFile.ACC_STATIC, code -> code.nop()));
        assertRefused(tmp, bytes, "Control flow falls through code end");
    }

    /**
     * StackMapTable presence. A conditional branch needs a stack-map frame at
     * its target for the split verifier; DROP_STACK_MAPS emits the branch with
     * no frame table.
     * <pre>
     *   static int bad(boolean b) {
     *       ILOAD_0
     *       IFNE L           // branch -- target L needs a stack-map frame
     *       ICONST_0; IRETURN
     *     L: ICONST_1; IRETURN
     *   }                    // (built without a StackMapTable)
     * </pre>
     */
    @Test
    void branchWithoutStackMap(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                .build(ClassDesc.of("NoMaps"), cb -> cb
                        .withMethodBody("bad", MethodTypeDesc.ofDescriptor("(Z)I"),
                                ClassFile.ACC_STATIC, code -> {
                                    Label l = code.newLabel();
                                    code.iload(0).ifne(l).iconst_0().ireturn()
                                        .labelBinding(l).iconst_1().ireturn();
                                }));
        assertRefused(tmp, bytes, "Expecting a stackmap frame");
    }

    /**
     * Operand-stack depth check: {@code pop} with nothing on the stack. Only
     * constructible with DROP_STACK_MAPS (the default builder rejects it at
     * build time).
     * <pre>
     *   static void boom() {
     *       POP              // stack is empty  <-- underflow
     *       RETURN
     *   }
     * </pre>
     */
    @Test
    void operandStackUnderflow(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                .build(ClassDesc.of("Underflow"), cb -> cb
                        .withMethodBody("boom", MethodTypeDesc.ofDescriptor("()V"),
                                ClassFile.ACC_STATIC, code -> code.pop().return_()));
        assertRefused(tmp, bytes, "Operand stack underflow");
    }

    /**
     * Local-variable store typing, the memory-safety case: an arbitrary int is
     * stored into a REFERENCE local ({@code astore}) and then dereferenced --
     * a fabricated pointer. The verifier rejects the astore (an int is not a
     * reference) before the getfield can dereference the forged address.
     * <pre>
     *   static int read() {
     *       SIPUSH 0x1000            // an arbitrary address as an int
     *       ASTORE_0                 // store it into a reference local  <-- int is not a reference
     *       ALOAD_0
     *       GETFIELD FakePtr.x : int // dereference the forged pointer
     *       IRETURN
     *   }
     * </pre>
     */
    @Test
    void fabricatedPointerDereference(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                .build(ClassDesc.of("FakePtr"), cb -> cb
                        .withMethodBody("read", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code
                                        .sipush(0x1000)
                                        .astore(0)
                                        .aload(0)
                                        .getfield(ClassDesc.of("FakePtr"), "x", ConstantDescs.CD_int)
                                        .ireturn()));
        assertRefused(tmp, bytes, "Bad type on operand stack");
    }

    /**
     * Uninitialized-object tracking: a method is invoked on a freshly
     * allocated object before its {@code <init>} constructor has run.
     * <pre>
     *   static int go() {
     *       NEW java/lang/Object            // uninitialized reference on the stack
     *       INVOKEVIRTUAL Object.hashCode() // used before <init>  <-- not yet initialized
     *       IRETURN
     *   }
     * </pre>
     */
    @Test
    void uninitializedObjectUse(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                .build(ClassDesc.of("Uninit"), cb -> cb
                        .withMethodBody("go", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code
                                        .new_(ConstantDescs.CD_Object)
                                        .invokevirtual(ConstantDescs.CD_Object, "hashCode",
                                                MethodTypeDesc.ofDescriptor("()I"))
                                        .ireturn()));
        assertRefused(tmp, bytes, "Bad type on operand stack");
    }

    /**
     * Exception-table check THROUGH THE HIERARCHY RESOLVER: a catch handler's
     * type must be assignable to {@code Throwable}, which only the class
     * hierarchy resolver can answer. Here the handler catches
     * {@code java.lang.String}, which is not a Throwable.
     * <pre>
     *   static void bad() {
     *     start: RETURN
     *       end:
     *   handler: POP; RETURN
     *       // exception table: [start,end) -> handler, type java/lang/String  <-- not a Throwable
     *   }
     * </pre>
     */
    @Test
    void catchTypeNotThrowable(@TempDir Path tmp) throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("BadCatch"), cb -> cb
                .withMethodBody("bad", MethodTypeDesc.ofDescriptor("()V"),
                        ClassFile.ACC_STATIC, code -> {
                            Label start = code.newBoundLabel();
                            code.return_();
                            Label end = code.newBoundLabel();
                            Label handler = code.newBoundLabel();
                            code.pop().return_();   // handler: caught value on stack
                            code.exceptionCatch(start, end, handler,
                                    ClassDesc.of("java.lang.String"));
                        }));
        assertRefused(tmp, bytes, "Catch type is not a subclass of Throwable");
    }

    /**
     * The shared refusal contract: the gate must reject the classfile with
     * exactly ONE diagnostic carrying the verifier's own message.
     */
    private static void assertRefused(Path tmp, byte[] bytes, String expectedMessage)
            throws Exception {
        Files.write(tmp.resolve("Input.class"), bytes);
        var reporter = analyze(tmp);

        // Exactly ONE diagnostic: a single planted defect must surface as a
        // single failure, and the gate refuses before the input-tier check,
        // so the (also-true) "no debug information" error never muddies it.
        assertEquals(1, reporter.diagnostics().size(),
                () -> "expected the single verification error, got: " + reporter.diagnostics());
        String message = reporter.diagnostics().getFirst().getMessage(null);
        assertTrue(message.contains("input failed JVM bytecode verification"),
                () -> "refusal should name the gate: " + message);
        // Assert the verifier's stable rule phrase only -- not the bytecode
        // offset or method descriptor, which are JDK-version-brittle.
        assertTrue(message.contains(expectedMessage),
                () -> "refusal should carry the VerifyError rule; expected \""
                        + expectedMessage + "\" in: " + message);
    }

    /**
     * The pooled-program-class layer of the hierarchy resolver is
     * load-bearing. Two program classes {@code A} and {@code B} (unrelated,
     * both under Object) and a method returning {@code B} where {@code A} is
     * required: the verifier can only judge the assignability -- and refuse
     * it -- because the pool supplies both classes' supertype. Delete the
     * pooled layer and both types become unresolvable, the class is tolerated
     * as having an incomplete hierarchy, and the input would (wrongly) pass --
     * so this refusal pins the layer that classpath loading cannot cover
     * (program classes live in a temp dir, unloadable).
     * <pre>
     *   class A {}               // program class, only in the pool
     *   class B {}               // program class, only in the pool
     *   static A pick(B b) {
     *       ALOAD_0              // push the B argument
     *       ARETURN              // return it as an A  <-- B is not assignable to A
     *   }
     * </pre>
     */
    @Test
    void pooledProgramHierarchyIsConsulted(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("A.class"),
                ClassFile.of().build(ClassDesc.of("A"), cb -> { }));
        Files.write(tmp.resolve("B.class"),
                ClassFile.of().build(ClassDesc.of("B"), cb -> { }));
        Files.write(tmp.resolve("Caller.class"),
                ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                        .build(ClassDesc.of("Caller"), cb -> cb
                                .withMethodBody("pick",
                                        MethodTypeDesc.of(ClassDesc.of("A"), ClassDesc.of("B")),
                                        ClassFile.ACC_STATIC, code -> code.aload(0).areturn())));

        var reporter = analyze(tmp);

        assertEquals(1, reporter.diagnostics().size(),
                () -> "expected one assignability error, got: " + reporter.diagnostics());
        String message = reporter.diagnostics().getFirst().getMessage(null);
        assertTrue(message.contains("input failed JVM bytecode verification")
                        && message.contains("is not assignable"),
                () -> "gate should judge assignability from the pooled hierarchy: " + message);
    }

    /**
     * Per-class scoping: one class's incomplete hierarchy never suppresses a
     * genuine defect in another. {@code Caller} references an unresolvable
     * type (tolerated, with a warning), while {@code Broken} has a real
     * defect (an int method returning void). The gate must still refuse on
     * {@code Broken}: tolerance is scoped to the class whose hierarchy is
     * incomplete, never shared across classes.
     * <pre>
     *   class Caller {           // hierarchy incomplete -> tolerated
     *       static com.nowhere.Missing pick(com.nowhere.Sub s) { ALOAD_0; ARETURN }
     *   }
     *   class Broken {           // genuine defect -> must still refuse
     *       static int bad() { RETURN }   // void return in an int method
     *   }
     * </pre>
     */
    @Test
    void oneClassesIncompleteHierarchyDoesNotExcuseAnother(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("Caller.class"),
                ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                        .build(ClassDesc.of("Caller"), cb -> cb
                                .withMethodBody("pick",
                                        MethodTypeDesc.of(ClassDesc.of("com.nowhere.Missing"),
                                                ClassDesc.of("com.nowhere.Sub")),
                                        ClassFile.ACC_STATIC, code -> code.aload(0).areturn())));
        Files.write(tmp.resolve("Broken.class"),
                ClassFile.of().build(ClassDesc.of("Broken"), cb -> cb
                        .withMethodBody("bad", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code.return_())));

        var reporter = analyze(tmp);

        assertEquals(2, reporter.diagnostics().size(),
                () -> "expected Caller's tolerance warning + Broken's refusal, got: "
                        + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("class Caller was not fully JVM-verified")),
                () -> "the tolerated class must be warned about: " + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("input failed JVM bytecode verification")
                                && d.getMessage(null).contains("Method expects a return value")),
                () -> "the genuine defect must be reported: " + reporter.diagnostics());
    }

    /**
     * Scoping holds WITHIN a class too: {@code verify()} checks all methods
     * of a class in one call, so a method referencing a missing dependency
     * must not drag a sibling method's genuine defect down with it when the
     * class is tolerated. The gate re-judges such a class one method at a
     * time (each method verified in a singleton copy of the class) and still
     * refuses on the genuine defect.
     * <pre>
     *   class Mixed {
     *       // hierarchy incomplete -> tolerated
     *       static com.nowhere.Missing unrelated(com.nowhere.Sub s) { ALOAD_0; ARETURN }
     *       // genuine defect -> must still refuse
     *       static int realDefect() { RETURN }   // void return in an int method
     *   }
     * </pre>
     */
    @Test
    void oneMethodsIncompleteHierarchyDoesNotExcuseSiblingMethod(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("Mixed.class"),
                ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                        .build(ClassDesc.of("Mixed"), cb -> cb
                                .withMethodBody("unrelated",
                                        MethodTypeDesc.of(ClassDesc.of("com.nowhere.Missing"),
                                                ClassDesc.of("com.nowhere.Sub")),
                                        ClassFile.ACC_STATIC, code -> code.aload(0).areturn())
                                .withMethodBody("realDefect", MethodTypeDesc.ofDescriptor("()I"),
                                        ClassFile.ACC_STATIC, code -> code.return_())));

        var reporter = analyze(tmp);

        assertEquals(2, reporter.diagnostics().size(),
                () -> "expected Mixed's tolerance warning + the sibling defect's refusal, got: "
                        + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("class Mixed was not fully JVM-verified")),
                () -> "the partially-verified class must be warned about: " + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("input failed JVM bytecode verification")
                                && d.getMessage(null).contains("Method expects a return value")
                                && d.getMessage(null).contains("realDefect")),
                () -> "the genuine defect must be reported: " + reporter.diagnostics());
    }

    /**
     * Per-class scoping holds across NESTED models of one unit too, not just
     * across top-level classes. Within {@code Outer}: one nested class has an
     * unresolvable type (tolerated, with a warning) and another has a genuine
     * defect. The defect must still be refused: tolerance never leaks across
     * the nested models of a unit within one {@code analyze()} call.
     * <pre>
     *   class Outer {
     *       static int ok() { ICONST_0; IRETURN }         // fine
     *   }
     *   class Outer$Unres {      // hierarchy incomplete -> tolerated
     *       static com.nowhere.Missing pick(com.nowhere.Sub s) { ALOAD_0; ARETURN }
     *   }
     *   class Outer$Broken {     // genuine defect -> must still refuse
     *       static int bad() { RETURN }   // void return in an int method
     *   }
     * </pre>
     */
    @Test
    void nestedModelIncompleteHierarchyDoesNotExcuseSibling(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("Outer.class"),
                ClassFile.of().build(ClassDesc.of("Outer"), cb -> cb
                        .withMethodBody("ok", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code.iconst_0().ireturn())));
        Files.write(tmp.resolve("Outer$Unres.class"),
                ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                        .build(ClassDesc.of("Outer$Unres"), cb -> cb
                                .with(java.lang.classfile.attribute.NestHostAttribute.of(
                                        ClassDesc.of("Outer")))
                                .withMethodBody("pick",
                                        MethodTypeDesc.of(ClassDesc.of("com.nowhere.Missing"),
                                                ClassDesc.of("com.nowhere.Sub")),
                                        ClassFile.ACC_STATIC, code -> code.aload(0).areturn())));
        Files.write(tmp.resolve("Outer$Broken.class"),
                ClassFile.of().build(ClassDesc.of("Outer$Broken"), cb -> cb
                        .with(java.lang.classfile.attribute.NestHostAttribute.of(ClassDesc.of("Outer")))
                        .withMethodBody("bad", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code.return_())));

        var reporter = analyze(tmp);

        assertEquals(2, reporter.diagnostics().size(),
                () -> "expected Outer$Unres's tolerance warning + Outer$Broken's refusal, got: "
                        + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        // dottedFqn display convention: nested $ renders as '.'
                        d.getMessage(null).contains("class Outer.Unres was not fully JVM-verified")),
                () -> "the tolerated nested class must be warned about: " + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("input failed JVM bytecode verification")
                                && d.getMessage(null).contains("Method expects a return value")
                                && d.getMessage(null).contains("Outer$Broken")),
                () -> "the genuine nested defect must be reported: " + reporter.diagnostics());
    }

    /**
     * An unverifiable NESTED classfile is caught too: {@code readClasses}
     * groups {@code Outer$Inner} under {@code Outer}, and the gate verifies
     * every model of the unit (see {@code allModels}), not just the top-level
     * class -- the only route by which inner-class bytecode reaches the gate.
     * The nested marker is a NestHost attribute (a bare {@code $} name is not
     * enough for {@code isNestedClass}).
     */
    @Test
    void unverifiableNestedClassfileIsRefused(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("Outer.class"),
                ClassFile.of().build(ClassDesc.of("Outer"), cb -> cb
                        .withMethodBody("ok", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code.iconst_0().ireturn())));
        Files.write(tmp.resolve("Outer$Inner.class"),
                ClassFile.of().build(ClassDesc.of("Outer$Inner"), cb -> cb
                        .with(java.lang.classfile.attribute.NestHostAttribute.of(ClassDesc.of("Outer")))
                        .withMethodBody("bad", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code.return_())));

        var reporter = analyze(tmp);

        assertEquals(1, reporter.diagnostics().size(),
                () -> "expected the nested class's single error, got: " + reporter.diagnostics());
        String message = reporter.diagnostics().getFirst().getMessage(null);
        assertTrue(message.contains("input failed JVM bytecode verification")
                        && message.contains("Method expects a return value")
                        && message.contains("Outer$Inner"),
                () -> "gate should verify the nested classfile: " + message);
    }

    /**
     * A type the frontend's classpath cannot resolve is NOT a verification
     * refusal: it means our classpath is incomplete, not that the input is
     * defective. {@code pick} returns a {@code Sub} where an unresolvable
     * {@code Missing} is required -- an assignability check that forces the
     * verifier to resolve {@code Missing} and fail. The gate detects the
     * empty resolver lookup for THIS class (not the JDK's message text),
     * warns that the class was not fully verified, and tolerates it, so the
     * input falls through to the input-tier check, which refuses it here only
     * because the handcrafted class carries no debug information. The
     * assertions bound the diagnostics to exactly that warning + tier error,
     * so a spurious verification refusal fails the count.
     * <pre>
     *   static com.nowhere.Missing pick(com.nowhere.Sub s) {
     *       ALOAD_0              // push the Sub argument
     *       ARETURN              // return it as Missing  <-- neither type resolvable
     *   }
     * </pre>
     */
    @Test
    void unresolvableTypeIsNotAVerificationRefusal(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("Caller.class"),
                ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                        .build(ClassDesc.of("Caller"), cb -> cb
                                .withMethodBody("pick",
                                        MethodTypeDesc.of(ClassDesc.of("com.nowhere.Missing"),
                                                ClassDesc.of("com.nowhere.Sub")),
                                        ClassFile.ACC_STATIC, code -> code.aload(0).areturn())));

        var reporter = analyze(tmp);   // still refused -- but by the input-tier gate

        assertEquals(2, reporter.diagnostics().size(),
                () -> "expected the tolerance warning + the input-tier error, got: "
                        + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().noneMatch(d ->
                        d.getMessage(null).contains("input failed JVM bytecode verification")),
                () -> "an unresolvable type must not be a verification refusal: "
                        + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("class Caller was not fully JVM-verified")),
                () -> "the tolerated class must be warned about: " + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("no debug information")),
                () -> "input should fall through to the input-tier gate: "
                        + reporter.diagnostics());
    }

    /**
     * Read every classfile under {@code tmp} and run the gate. Returns the
     * reporter carrying whatever the gate reported; asserts the input was
     * refused ({@link RefusedInputException}).
     */
    private static Reporter analyze(Path tmp) throws Exception {
        var classes = BytecodeFrontend.readClasses(tmp);
        var reporter = new Reporter();
        assertThrows(RefusedInputException.class,
                () -> new BytecodeToLaurelCompiler(reporter, true, List.of())
                        .analyze(List.of(tmp.resolve("Input.java").toUri()), classes));
        return reporter;
    }

    /**
     * Pre-Java-6 bytecode (major version &lt; 50) uses the old inference
     * verifier, which {@code java.lang.classfile} cannot run: {@code verify()}
     * would report "Inference verification is not supported" as if the input
     * were defective. The gate instead refuses on the version field itself --
     * a structured value, not a message string -- with an honest message and
     * no leaked JDK wording.
     */
    @Test
    void unsupportedOldClassVersionIsRefusedExplicitly(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("Old.class"),
                ClassFile.of().build(ClassDesc.of("Old"), cb -> cb
                        .withVersion(49, 0)   // Java 5
                        .withMethodBody("m", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code.iconst_0().ireturn())));

        var reporter = analyze(tmp);

        assertEquals(1, reporter.diagnostics().size(),
                () -> "expected the single version error, got: " + reporter.diagnostics());
        String message = reporter.diagnostics().getFirst().getMessage(null);
        assertTrue(message.contains("unsupported class file version (49)"),
                () -> "refusal should name the version: " + message);
        assertTrue(!message.contains("Inference verification"),
                () -> "must not leak the JDK's inference-verifier message: " + message);
    }

    /**
     * Tolerance is not a refusal in disguise: a class whose hierarchy cannot
     * be fully resolved must REACH TRANSLATION, not just avoid the
     * verification error. Real javac output (FULL tier, so the input-tier
     * gate stays silent) whose dependency classfile is deleted after
     * compilation: the assignability check needs the deleted type, the gate
     * warns and tolerates, and {@code analyze} completes with a translated
     * program -- no {@code RefusedInputException}. Deleting the gate's
     * tolerance path entirely would fail this test.
     */
    @Test
    void toleratedClassStillReachesTranslation(@TempDir Path tmp) throws Exception {
        // Widening must target a non-Object supertype: everything is
        // assignable to Object without consulting the hierarchy resolver, so
        // only the Dep -> Base check forces the (deleted) Dep to resolve.
        var base = new SourceFile(tmp.resolve("Base.java"), "class Base { }");
        var dep = new SourceFile(tmp.resolve("Dep.java"), "class Dep extends Base { }");
        var use = new SourceFile(tmp.resolve("Use.java"), """
                class Use {
                    static Base widen(Dep d) {
                        return d;
                    }
                }
                """);
        Path classes = compile(tmp, base, dep, use);
        Files.delete(classes.resolve("Dep.class"));   // make Dep unresolvable

        var readClasses = BytecodeFrontend.readClasses(classes);
        var reporter = new Reporter();
        var analysis = new BytecodeToLaurelCompiler(reporter, true, List.of())
                .analyze(List.of(use.toUri()), readClasses);   // must NOT throw

        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("class Use was not fully JVM-verified")),
                () -> "the tolerated class must be warned about: " + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().noneMatch(d ->
                        d.getMessage(null).contains("input failed JVM bytecode verification")),
                () -> "tolerance must not be a verification refusal: " + reporter.diagnostics());
        assertTrue(analysis != null && analysis.program() != null,
                "translation must proceed past the gate");
    }

    /**
     * A malformed {@code this_class} name (impossible to build with the
     * ClassFile API, which validates names -- so the valid name is
     * byte-patched to contain dots) must be refused, never crash: name
     * validation happens inside {@code ClassDesc}, which the gate reaches
     * through the verifier, and {@code judge} converts the throw into a
     * refusal.
     */
    @Test
    void malformedClassNameIsRefusedNotCrashedOn(@TempDir Path tmp) throws Exception {
        byte[] valid = ClassFile.of().build(ClassDesc.of("AAAAA"), cb -> cb
                .withMethodBody("m", MethodTypeDesc.ofDescriptor("()I"),
                        ClassFile.ACC_STATIC, code -> code.iconst_0().ireturn()));
        byte[] patched = patch(valid, "AAAAA", "A.A.A");   // dots are illegal in an internal name
        Files.write(tmp.resolve("Patched.class"), patched);

        var reporter = analyze(tmp);   // refused, never a crash

        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("input failed JVM bytecode verification")),
                () -> "a malformed class name must be refused: " + reporter.diagnostics());
    }

    /** Replace the single occurrence of {@code needle}'s ASCII bytes in {@code bytes}. */
    private static byte[] patch(byte[] bytes, String needle, String replacement) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] r = replacement.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(n.length, r.length, "in-place patch needs equal lengths");
        outer:
        for (int i = 0; i <= bytes.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (bytes[i + j] != n[j]) {
                    continue outer;
                }
            }
            byte[] out = bytes.clone();
            System.arraycopy(r, 0, out, i, r.length);
            return out;
        }
        throw new AssertionError("needle not found: " + needle);
    }

    /**
     * The version boundary is exact: v50 (Java 6, the first StackMapTable
     * release) must PASS the version gate -- only v49 and below are refused.
     * An off-by-one would silently refuse all Java 6+ bytecode. The fixture
     * still ends refused by the input-tier gate (no debug information), so
     * the assertion is that no version error appears.
     */
    @Test
    void java6ClassVersionPassesTheVersionGate(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("Boundary.class"),
                ClassFile.of().build(ClassDesc.of("Boundary"), cb -> cb
                        .withVersion(ClassFile.JAVA_6_VERSION, 0)
                        .withMethodBody("m", MethodTypeDesc.ofDescriptor("()I"),
                                ClassFile.ACC_STATIC, code -> code.iconst_0().ireturn())));

        var reporter = analyze(tmp);   // refused by the input-tier gate only

        assertTrue(reporter.diagnostics().stream().noneMatch(d ->
                        d.getMessage(null).contains("unsupported class file version")),
                () -> "v50 must pass the version gate: " + reporter.diagnostics());
        assertTrue(reporter.diagnostics().stream().anyMatch(d ->
                        d.getMessage(null).contains("no debug information")),
                () -> "the refusal should come from the input-tier gate: "
                        + reporter.diagnostics());
    }

    /**
     * The STRUCTURAL failure level, upstream of the gate: bytes that cannot
     * even be parsed into a {@code ClassModel} (truncated, corrupted, not a
     * classfile). {@code readClasses} catches the parser's
     * {@code IllegalArgumentException} and rethrows it as the typed refusal
     * (file name + the parser's own reason) that the driver maps to the same
     * reported-error + exit-2 outcome as the gate -- never a bare parser
     * crash. The JVM verifier plays no part at this level: there is no model
     * to verify. Input generated like the others: a VALID class built with
     * the ClassFile API, then deterministically truncated.
     */
    @Test
    void unparseableClassfileIsRefusedNotCrashedOn(@TempDir Path tmp) throws Exception {
        byte[] valid = ClassFile.of().build(ClassDesc.of("Truncated"), cb -> cb
                .withMethodBody("good", MethodTypeDesc.ofDescriptor("()I"),
                        ClassFile.ACC_STATIC, code -> code.iconst_0().ireturn()));
        Files.write(tmp.resolve("Truncated.class"), java.util.Arrays.copyOf(valid, valid.length / 2));

        var e = assertThrows(org.strata.java.bytecode.frontend.UnparseableClassfileException.class,
                () -> BytecodeFrontend.readClasses(tmp));

        assertTrue(e.getMessage().contains("classfile Truncated.class is not parseable"),
                () -> "refusal should name the file: " + e.getMessage());
        // The parser's own reason, verbatim.
        assertTrue(e.getMessage().contains("Reading beyond classfile bounds"),
                () -> "refusal should carry the parser's reason: " + e.getMessage());
    }

    /**
     * Damage in a LAZILY-parsed region (the method/attribute tail) that plain
     * {@code parse} tolerates must still be caught, because {@code readClasses}
     * forces a full parse (materialize) inside its guard. Without that forced
     * parse the corruption would surface as a raw crash later in translation.
     * The last bytes of a valid class are flipped: {@code parse} returns a
     * model, but the full read throws, so readClasses refuses it.
     */
    @Test
    void corruptLazyRegionIsCaughtByMaterialize(@TempDir Path tmp) throws Exception {
        byte[] valid = ClassFile.of().build(ClassDesc.of("BadTail"), cb -> cb
                .withField("f", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC)
                .withMethodBody("m", MethodTypeDesc.ofDescriptor("()I"),
                        ClassFile.ACC_STATIC, code -> code.iconst_0().ireturn()));
        byte[] corruptTail = valid.clone();
        for (int i = corruptTail.length - 3; i < corruptTail.length; i++) {
            corruptTail[i] = (byte) 0xFF;
        }
        Files.write(tmp.resolve("BadTail.class"), corruptTail);

        var e = assertThrows(org.strata.java.bytecode.frontend.UnparseableClassfileException.class,
                () -> BytecodeFrontend.readClasses(tmp));
        assertTrue(e.getMessage().contains("BadTail.class is not parseable"),
                () -> "a corrupt lazy region must be refused, not crash later: " + e.getMessage());
    }

    @Test
    void verifiableJavacOutputPassesTheGate(@TempDir Path tmp) throws Exception {
        // Happy path through the same entry point: ordinary javac output
        // (which always verifies) must reach translation with no errors.
        var source = new SourceFile(tmp.resolve("Ok.java"), """
                class Ok {
                    static int one() {
                        return 1;
                    }
                }
                """);
        var classes = BytecodeFrontend.readClasses(compile(tmp, source));
        var reporter = new Reporter();

        var analysis = new BytecodeToLaurelCompiler(reporter, true, List.of())
                .analyze(List.of(source.toUri()), classes);

        assertTrue(reporter.diagnostics().stream()
                        .noneMatch(d -> d.getMessage(null).contains("JVM bytecode verification")),
                () -> "gate must be silent on verifiable input: " + reporter.diagnostics());
        assertTrue(analysis.program().staticProcedures().stream()
                        .anyMatch(p -> p.name().text().contains("one")),
                "translation must proceed past the gate");
    }

    private static Path compile(Path tmp, JavaFileObject... sources) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var collector = new DiagnosticCollector<JavaFileObject>();
        Path classes = tmp.resolve("classes");
        Files.createDirectories(classes);
        try (var fm = compiler.getStandardFileManager(collector, null, null)) {
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            assertTrue(compiler.getTask(new PrintWriter(new StringWriter()), fm, collector,
                            List.of("-g", "-Xjcov", "-proc:none"), null, List.of(sources)).call(),
                    () -> "javac failed: " + collector.getDiagnostics());
        }
        return classes;
    }
}
