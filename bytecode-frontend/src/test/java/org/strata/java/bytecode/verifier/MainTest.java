package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code bytecode-verifier} command line, end to end (Strata on PATH).
 */
class MainTest {

    private static final String NAT_PARAM = """
            package p;

            import org.strata.jverify.Nat;
            import static org.strata.jverify.JVerify.check;

            class NatParam {
                static void m(@Nat int n) {
                    check(n >= 0);
                }
            }
            """;

    private static final String FAILS = """
            package p;

            import static org.strata.jverify.JVerify.check;

            class Fails {
                static void m() {
                    check(false);
                }
            }
            """;

    /** This frontend's options that JFE does not have. */
    private static final Set<String> BYTECODE_FRONTEND_ONLY = Set.of("--no-constant-folding");

    /** JFE's options this command does not implement, rejected as unknown. */
    private static final Set<String> SOURCE_FRONTEND_ONLY =
            Set.of("--filter-position", "--include-filter-dependencies");

    private record Run(int exitCode, String out, String err) {
    }

    private static Run run(String... args) {
        return run(Map.of(), args);
    }

    /** Run with {@code env} as the command's environment variables. */
    private static Run run(Map<String, String> env, String... args) {
        var out = new StringWriter();
        var err = new StringWriter();
        int exitCode = Main.run(args, new PrintWriter(out, true), new PrintWriter(err, true), env::get);
        return new Run(exitCode, out.toString(), err.toString());
    }

    private static Path write(Path dir, String name, String source) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, source);
        return file;
    }

    /** {@code sources} compiled into {@code tmp}'s {@code classes} directory, which is returned. */
    private static Path compile(Path tmp, Path... sources) throws Exception {
        Path classes = Files.createDirectories(tmp.resolve("classes"));
        var args = new ArrayList<>(List.of("-d", classes.toString()));
        for (Path source : sources) {
            args.add(source.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        return classes;
    }

    @Test
    void natParameterIsAssumed(@TempDir Path tmp) throws Exception {
        var r = run(write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(0, r.exitCode(), r::toString);
        assertTrue(r.out().contains("Found 0 errors"), r::toString);
    }

    @Test
    void aDeletedSpecLibraryIsExtractedAgain(@TempDir Path tmp) throws Exception {
        Files.delete(Main.specLibrary());
        var r = run(write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(0, r.exitCode(), r::toString);
    }

    /** {@code :library} itself on the classpath, whose {@code @Nat} javac drops, does not shadow the command's. */
    @Test
    void natParameterIsAssumedWithTheLibraryOnTheClasspath(@TempDir Path tmp) throws Exception {
        var nat = Class.forName("org.strata.jverify.Nat");
        assertEquals(RetentionPolicy.SOURCE, nat.getAnnotation(Retention.class).value());
        var library = Path.of(nat.getProtectionDomain().getCodeSource().getLocation().toURI());
        var r = run(write(tmp, "NatParam.java", NAT_PARAM).toString(), "--classpath", library.toString());
        assertEquals(0, r.exitCode(), r::toString);
        assertTrue(r.out().contains("Found 0 errors"), r::toString);
    }

    /** {@code MyEx} extends {@code dep.Mid}, which only --classpath has, not this JVM. */
    private static final String USE_DEP = """
            package p;

            import static org.strata.jverify.JVerify.check;

            class MyEx extends dep.Mid {
            }

            class UseDep {
                static void m(RuntimeException e) {
                    if (e instanceof MyEx) {
                        check(false);
                    }
                }
            }
            """;

    /**
     * A superclass from --classpath, and its own, are part of the hierarchy, even when a class
     * their methods name is missing, as an optional dependency's can be: {@code e} may be a
     * {@code MyEx}, so {@code check(false)} is not proved.
     */
    @Test
    void aClasspathSuperclassIsPartOfTheHierarchy(@TempDir Path tmp) throws Exception {
        Path dep = Files.createDirectories(tmp.resolve("dep"));
        Path classes = compile(tmp,
                write(dep, "Base.java", "package dep;\npublic class Base extends RuntimeException {\n}\n"),
                write(dep, "Mid.java",
                        "package dep;\npublic class Mid extends Base {\n    public void f(Gone g) {\n    }\n}\n"),
                write(dep, "Gone.java", "package dep;\npublic class Gone {\n}\n"));
        Files.delete(classes.resolve("dep/Gone.class"));
        var r = run(write(tmp, "UseDep.java", USE_DEP).toString(), "--classpath", classes.toString());
        assertEquals(4, r.exitCode(), r::toString);
        assertTrue(r.out().contains("assertion could not be proved"), r::toString);
    }

    /**
     * {@code Impl} overrides the contracted {@code dep.I.m} through {@code dep.J}, an interface only
     * --classpath has: the override is found, so the call is refused, not verified against the contract.
     */
    @Test
    void aClasspathInterfaceIsPartOfTheHierarchy(@TempDir Path tmp) throws Exception {
        Path dep = Files.createDirectories(tmp.resolve("dep"));
        Path classes = compile(tmp,
                write(dep, "I.java", "package dep;\npublic interface I {\n    int m();\n}\n"),
                write(dep, "J.java", "package dep;\npublic interface J extends I {\n}\n"));
        write(Files.createDirectories(tmp.resolve("contracts")), "IContract.java", """
                import org.strata.jverify.Contract;
                import org.strata.jverify.ContractException;

                import static org.strata.jverify.JVerify.postcondition;

                @Contract(dep.I.class)
                class IContract {
                    public int m() {
                        postcondition((int r) -> r == 1);
                        throw new ContractException();
                    }
                }
                """);
        var r = run(write(tmp, "Uses.java", """
                package p;

                import static org.strata.jverify.JVerify.check;

                class Uses {
                    static class Impl implements dep.J {
                        public int m() {
                            return 2;
                        }
                    }

                    static void use(dep.I i) {
                        check(i.m() == 1);
                    }
                }
                """).toString(), "--classpath", classes.toString(), "--contract-path", tmp.resolve("contracts").toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.out().contains("overridden by 'p.Uses.Impl'"), r::toString);
    }

    /**
     * A --classpath class only an {@code instanceof} names is found, whether nested
     * ({@code dep.Outer.Inner}, {@code dep.Outer.Mi$d}) or with a {@code $} in its own
     * top-level name ({@code dep.Gen$Ex}).
     */
    @Test
    void aClasspathClassOnlyAnInstanceofNamesIsFound(@TempDir Path tmp) throws Exception {
        Path classes = compile(tmp,
                write(tmp, "Outer.java", "package dep;\npublic class Outer {\n"
                        + "    public static class Inner extends RuntimeException {\n    }\n"
                        + "    public static class Mi$d extends RuntimeException {\n    }\n}\n"),
                write(tmp, "Gen$Ex.java", "package dep;\npublic class Gen$Ex extends RuntimeException {\n}\n"));
        for (var name : List.of("dep.Outer.Inner", "dep.Outer.Mi$d", "dep.Gen$Ex")) {
            var r = run(write(tmp, "UseDep.java", """
                    package p;

                    import static org.strata.jverify.JVerify.check;

                    class UseDep {
                        static void m(RuntimeException e) {
                            if (e instanceof %s) {
                                check(false);
                            }
                        }
                    }
                    """.formatted(name)).toString(), "--classpath", classes.toString());
            assertEquals(4, r.exitCode(), () -> name + ": " + r);
            assertTrue(r.out().contains("assertion could not be proved"), () -> name + ": " + r);
        }
    }

    /** javac reads a class of a package the JDK does not export, {@code sun.nio.ch.Mid}, from --classpath. */
    @Test
    void aClassOfAnUnexportedJdkPackageComesFromTheClasspath(@TempDir Path tmp) throws Exception {
        var runtimeException = ClassDesc.of("java.lang.RuntimeException");
        Path classes = tmp.resolve("classes");
        Files.write(Files.createDirectories(classes.resolve("sun/nio/ch")).resolve("Mid.class"),
                ClassFile.of().build(ClassDesc.of("sun.nio.ch.Mid"), c -> c
                        .withFlags(ClassFile.ACC_PUBLIC)
                        .withSuperclass(runtimeException)
                        .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, b -> b
                                .aload(0).invokespecial(runtimeException, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                .return_())));
        var r = run(write(tmp, "UseDep.java", USE_DEP.replace("dep.Mid", "sun.nio.ch.Mid")).toString(),
                "--classpath", classes.toString());
        assertEquals(4, r.exitCode(), r::toString);
        assertTrue(r.out().contains("assertion could not be proved"), r::toString);
    }

    /**
     * A --classpath class named like one of this verifier's own, Ion's interface {@code IonValue} or
     * JUnit's {@code AssertionsKt}, whose methods reflection cannot read here without Kotlin, is the
     * one javac read: {@code o} is a {@code RuntimeException}, so {@code check(false)} is not proved.
     */
    @Test
    void aClasspathClassNamedLikeOneOfTheVerifiersIsTheClasspaths(@TempDir Path tmp) throws Exception {
        Path classes = compile(tmp,
                write(Files.createDirectories(tmp.resolve("ion")), "IonValue.java",
                        "package com.amazon.ion;\npublic class IonValue extends RuntimeException {\n}\n"),
                write(Files.createDirectories(tmp.resolve("junit")), "AssertionsKt.java",
                        "package org.junit.jupiter.api;\npublic class AssertionsKt {\n}\n"));
        var r = run(write(tmp, "Namesakes.java", """
                package p;

                import static org.strata.jverify.JVerify.check;

                class MyEx extends com.amazon.ion.IonValue {
                }

                class Sub extends org.junit.jupiter.api.AssertionsKt {
                    int f() {
                        return 1;
                    }
                }

                class Namesakes {
                    static void m() {
                        Object o = new MyEx();
                        if (o instanceof RuntimeException) {
                            check(false);
                        }
                    }
                }
                """).toString(), "--classpath", classes.toString());
        assertEquals(4, r.exitCode(), r::toString);
        assertTrue(r.out().contains("assertion could not be proved"), r::toString);
    }

    /** Sources on --classpath are not compiled: javac reports the class they declare missing. */
    @Test
    void classpathSourcesAreNotCompiled(@TempDir Path tmp) throws Exception {
        Path sources = tmp.resolve("sources");
        write(Files.createDirectories(sources.resolve("dep")), "Mid.java",
                "package dep;\npublic class Mid extends RuntimeException {\n}\n");
        var r = run(write(tmp, "UseDep.java", USE_DEP).toString(), "--classpath", sources.toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.out().contains("package dep does not exist"), r::toString);
    }

    @Test
    void failingCheckIsReported(@TempDir Path tmp) throws Exception {
        var r = run(write(tmp, "Fails.java", FAILS).toString());
        assertEquals(4, r.exitCode(), r::toString);
        assertTrue(r.out().contains("Fails.java(7:9-7:22): Error: assertion does not hold"), r::toString);
        assertTrue(r.out().contains("Found 1 errors"), r::toString);
    }

    @Test
    void pathsShowsTheFilePath(@TempDir Path tmp) throws Exception {
        var file = write(tmp, "Fails.java", FAILS);
        var r = run("--paths", file.toString());
        assertEquals(4, r.exitCode(), r::toString);
        assertTrue(r.out().contains(file + "(7:9-7:22): Error: assertion does not hold"), r::toString);
    }

    /** A loop the verifier cannot see through without an invariant, but the interpreter just runs. */
    private static final String LOOP = """
            package p;

            import org.strata.jverify.InterpretEntry;
            import static org.strata.jverify.JVerify.check;

            class Loop {
                @InterpretEntry
                static void run() {
                    int x = 0;
                    while (x < 300) {
                        x = x + 1;
                    }
                    check(x == 300);
                }

                static void fails() {
                    check(false);
                }
            }
            """;

    /**
     * {@code --interpret} runs the {@code @InterpretEntry} method (which needs that
     * annotation in the classfile) instead of verifying: the check verification
     * cannot prove holds when run.
     */
    @Test
    void interpretRunsTheEntryInsteadOfVerifying(@TempDir Path tmp) throws Exception {
        var file = write(tmp, "Loop.java", LOOP).toString();
        var verified = run(file);
        assertEquals(4, verified.exitCode(), verified::toString);
        var interpreted = run("--interpret", file);
        assertEquals(0, interpreted.exitCode(), interpreted::toString);
        assertTrue(interpreted.out().contains("Found 0 errors"), interpreted::toString);
    }

    @Test
    void entrySelectsTheMethodToRun(@TempDir Path tmp) throws Exception {
        var r = run("--interpret", "--entry", "p.Loop#fails", write(tmp, "Loop.java", LOOP).toString());
        assertEquals(4, r.exitCode(), r::toString);
        assertTrue(r.out().contains("Found 1 errors"), r::toString);
    }

    @Test
    void fuelBoundsTheRun(@TempDir Path tmp) throws Exception {
        var r = run("--interpret", "--fuel", "50", write(tmp, "Loop.java", LOOP).toString());
        assertTrue(r.exitCode() != 0, r::toString);
        assertFalse(r.out().contains("Found 0 errors"), r::toString);
    }

    private static final String RECURSION = """
            package p;

            import static org.strata.jverify.JVerify.check;

            class Rec {
                static int recurse(int n) {
                    return recurse(n + 1);
                }

                static void run() {
                    check(recurse(0) == 0);
                }
            }
            """;

    /**
     * {@code --interpreter laurel} reaches Strata: only the Laurel interpreter,
     * whose fuel bounds the whole entry rather than each call, stops unbounded
     * recursion by running out of fuel.
     */
    @Test
    void interpreterSelectsTheLaurelInterpreter(@TempDir Path tmp) throws Exception {
        var file = write(tmp, "Rec.java", RECURSION).toString();
        var laurel = run("--interpret", "--interpreter", "laurel", "--entry", "p.Rec#run", file);
        assertEquals(2, laurel.exitCode(), laurel::toString);
        assertTrue(laurel.out().contains("out of fuel"), laurel::toString);
        var core = run("--interpret", "--entry", "p.Rec#run", file);
        assertEquals(2, core.exitCode(), core::toString);
        assertFalse(core.out().contains("out of fuel"), core::toString);
    }

    @Test
    void verifyByDefaultFalseSkipsUnannotatedMethods(@TempDir Path tmp) throws Exception {
        var r = run("--verify-by-default=false", write(tmp, "Fails.java", FAILS).toString());
        assertEquals(0, r.exitCode(), r::toString);
        assertTrue(r.out().contains("Verified methods: 0"), r::toString);
    }

    @Test
    void trackTimeReportsThePhases(@TempDir Path tmp) throws Exception {
        var r = run("--track-time", write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(0, r.exitCode(), r::toString);
        assertTrue(r.out().contains("Running Strata took"), r::toString);
    }

    @Test
    void verboseShowsStrataOutput(@TempDir Path tmp) throws Exception {
        var file = write(tmp, "NatParam.java", NAT_PARAM).toString();
        assertFalse(run(file).out().contains("==== DIAGNOSTICS ===="));
        var r = run("--verbose", file);
        assertEquals(0, r.exitCode(), r::toString);
        assertTrue(r.out().contains("==== DIAGNOSTICS ===="), r::toString);
    }

    /** The program is written and nothing is verified: a failing check exits 0. */
    @Test
    void emitLaurelWritesTheProgramWithoutVerifying(@TempDir Path tmp) throws Exception {
        var ion = tmp.resolve("out/program.ion");
        var r = run("--emit-laurel", ion.toString(), write(tmp, "Fails.java", FAILS).toString());
        assertEquals(0, r.exitCode(), r::toString);
        assertTrue(Files.size(ion) > 0, r::toString);
        assertFalse(r.out().contains("assertion does not hold"), r::toString);
    }

    @Test
    void missingContractPathIsRefused(@TempDir Path tmp) throws Exception {
        var missing = tmp.resolve("no-such-contracts");
        var r = run(write(tmp, "NatParam.java", NAT_PARAM).toString(), "--contract-path", missing.toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.out().contains(missing.getFileName().toString()), r::toString);
    }

    @Test
    void missingInputIsAUsageError(@TempDir Path tmp) {
        var missing = tmp.resolve("Missing.java");
        var r = run(missing.toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("Cannot read input file " + missing), r::toString);
    }

    /** A source in Latin-1, where UTF-8 is read, is reported as a Java error rather than thrown. */
    @Test
    void anInputThatIsNotUtf8IsAJavaError(@TempDir Path tmp) throws Exception {
        Path latin1 = tmp.resolve("Latin1.java");
        Files.write(latin1, "// café\nclass Latin1 {\n}\n".getBytes(StandardCharsets.ISO_8859_1));
        var r = run(latin1.toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.out().contains("error reading " + latin1 + "; not valid UTF-8"), r::toString);
    }

    @Test
    void emitLaurelToADirectoryIsAUsageError(@TempDir Path tmp) throws Exception {
        var r = run("--emit-laurel", tmp.toString(), write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("Cannot write --emit-laurel file " + tmp + ": it is a directory"), r::toString);
    }

    @Test
    void sourceFrontendOnlyOptionIsRejected(@TempDir Path tmp) throws Exception {
        var r = run("--filter-position=NatParam.java:7", write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("Unknown option"), r::toString);
    }

    @Test
    void zeroFuelIsRejected(@TempDir Path tmp) throws Exception {
        var r = run("--interpret", "--fuel", "0", write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("must be > 0"), r::toString);
    }

    @Test
    void unknownInterpreterIsRejected(@TempDir Path tmp) throws Exception {
        var r = run("--interpret", "--interpreter", "lean", write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("expected core or laurel"), r::toString);
    }

    @Test
    void interpreterRequiresInterpret(@TempDir Path tmp) throws Exception {
        var r = run("--interpreter", "laurel", write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("requires '--interpret'"), r::toString);
    }

    @Test
    void strataWithoutStrataCliIsAUsageError(@TempDir Path tmp) throws Exception {
        var input = write(tmp, "NatParam.java", NAT_PARAM).toString();
        for (var dir : new Path[] {tmp, tmp.resolve("missing")}) {
            var r = run("--strata", dir.toString(), input);
            assertEquals(2, r.exitCode(), r::toString);
            assertTrue(r.err().contains("Not a Strata project directory (--strata): " + dir
                    + " has no StrataCLI subdirectory"), r::toString);
        }
    }

    @Test
    void jverifyStrataIsTheDefaultForStrata(@TempDir Path tmp) throws Exception {
        var input = write(tmp, "NatParam.java", NAT_PARAM).toString();
        var r = run(Map.of("JVERIFY_STRATA", tmp.toString()), input);
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("(JVERIFY_STRATA): " + tmp), r::toString);
        assertTrue(r.err().contains("unset it to run `strata` from PATH"), r::toString);

        var strata = tmp.resolve("other");
        r = run(Map.of("JVERIFY_STRATA", tmp.toString()), "--strata", strata.toString(), input);
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("(--strata): " + strata), r::toString);
    }

    /** The user guide's pitfall: the StrataCLI subdirectory instead of the project directory. */
    @Test
    void strataCliItselfIsAUsageErrorNamingItsParent(@TempDir Path tmp) throws Exception {
        var strataCli = Files.createDirectory(tmp.resolve("StrataCLI"));
        var r = run("--strata", strataCli.toString(), write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(2, r.exitCode(), r::toString);
        assertTrue(r.err().contains("pass the Strata project directory, its parent"), r::toString);
    }

    /**
     * With a project directory, Strata runs through {@code lake} there, not from
     * PATH, where this test's {@code strata} would verify the input. (The empty
     * StrataCLI has no lakefile, so the run fails whether or not {@code lake} is
     * on PATH.)
     */
    @Test
    void aProjectDirectoryRunsStrataThroughLakeNotFromPath(@TempDir Path tmp) throws Exception {
        Files.createDirectory(tmp.resolve("StrataCLI"));
        var r = run("--strata", tmp.toString(), write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertTrue(r.exitCode() != 0, r::toString);
        assertFalse(r.out().contains("Found 0 errors"), r::toString);
    }

    /** An empty JVERIFY_STRATA is unset: Strata runs from PATH. */
    @Test
    void emptyJverifyStrataRunsStrataFromPath(@TempDir Path tmp) throws Exception {
        var r = run(Map.of("JVERIFY_STRATA", ""), write(tmp, "NatParam.java", NAT_PARAM).toString());
        assertEquals(0, r.exitCode(), r::toString);
        assertTrue(r.out().contains("Found 0 errors"), r::toString);
    }

    /**
     * Operations javac folds, {@code 6 & 3} to 2 and {@code "a" + "b" == "ab"} to true (a constant String
     * is interned). Unfolded, the translation sees the {@code &} and the {@code ==} on Strings, and refuses
     * both, as JFE does.
     */
    private static final String REFUSED = """
            package p;

            import static org.strata.jverify.JVerify.check;

            class Refused {
                static void and() {
                    check((6 & 3) == 2);
                }

                static void strings() {
                    check("a" + "b" == "ab");
                }
            }
            """;

    @Test
    void noConstantFoldingVerifiesTheOperationsTheSourceWrites(@TempDir Path tmp) throws Exception {
        var file = write(tmp, "Refused.java", REFUSED).toString();
        var folded = run(file);
        assertEquals(0, folded.exitCode(), folded::toString);
        var unfolded = run("--no-constant-folding", file);
        assertEquals(2, unfolded.exitCode(), unfolded::toString);
        assertTrue(unfolded.out().contains("operator &(int,int) is not supported"), unfolded::toString);
        assertTrue(unfolded.out().contains("'==' is only allowed when at least one operand's type is impure"),
                unfolded::toString);
    }

    private static final String OVERFLOW = """
            package p;

            import static org.strata.jverify.JVerify.check;

            class Ovf {
                static void wraps() {
                    check(65536 * 65536 == 0);
                    check(Integer.MAX_VALUE + 1 < 0);
                    check(Integer.MIN_VALUE - 1 > 0);
                    check(Integer.MIN_VALUE / -1 < 0);
                    check(Long.MAX_VALUE + 1 < 0);
                    check(-Long.MIN_VALUE < 0);
                }
            }
            """;

    /**
     * An overflowing constant expression stays folded with --no-constant-folding: the frontend
     * computes int and long arithmetic without wrapping, so unfolded it would refute
     * these checks, and prove their negations.
     */
    @Test
    void noConstantFoldingKeepsAnOverflowingConstantFolded(@TempDir Path tmp) throws Exception {
        var holds = run("--no-constant-folding", write(tmp, "Ovf.java", OVERFLOW).toString());
        assertEquals(0, holds.exitCode(), holds::toString);
        Path negated = Files.createDirectories(tmp.resolve("negated"));
        var fails = run("--no-constant-folding", write(negated, "Ovf.java",
                OVERFLOW.replaceAll("check\\((.*)\\);", "check(!($1));")).toString());
        assertEquals(4, fails.exitCode(), fails::toString);
    }

    /**
     * Constant operations where the frontend reads code specially: at the joins of an if, a ?:, a
     * switch expression and an &&, and in contract clauses, a finally block and a quantifier's body.
     */
    private static final String PLACES = """
            package p;

            import static org.strata.jverify.JVerify.check;
            import static org.strata.jverify.JVerify.forall;
            import static org.strata.jverify.JVerify.implies;
            import static org.strata.jverify.JVerify.precondition;
            import static org.strata.jverify.JVerify.throwsOn;

            class Places {
                static class Failure extends Exception {}

                static void branch() {
                    int x = 0;
                    if (2 * 3 == 6) {
                        x = 1;
                    }
                    check(x == 1);
                }

                static void ternary() {
                    int x = (5 > 3) ? 10 : 20;
                    check(x == 10);
                }

                static void switchArm() {
                    int k = 1;
                    int v = switch (k) { case 1 -> 2 * 3; default -> 7 / 2; };
                    check(v == 6);
                }

                static void underAnAnd() {
                    final int c = 20;
                    int v = 0;
                    v += ((false && (10 > (-6))) ? ((short) 5) : (((-11) - c) - ((short) 1)));
                    check(v == -32);
                }

                static void pre(int n) {
                    precondition(n == 1 + 1);
                    check(n == 2);
                }

                static void callsPre() {
                    pre(2);
                }

                static int throwing(int b) throws Failure {
                    throwsOn(b == 1 - 1);
                    if (b == 0) {
                        throw new Failure();
                    }
                    return b;
                }

                static void finallyBlock() {
                    int r = 0;
                    try {
                        r = 1;
                    } finally {
                        r = r + 2 * 3;
                    }
                    check(r == 7);
                }

                static void quantifier() {
                    check(forall((int i) -> implies(i > 2 * 3, i > 6)));
                }
            }
            """;

    @Test
    void noConstantFoldingVerifiesWhatFoldingVerifies(@TempDir Path tmp) throws Exception {
        var places = write(tmp, "Places.java", PLACES).toString();
        for (var args : List.of(List.of(places), List.of("--no-constant-folding", places))) {
            var r = run(args.toArray(String[]::new));
            assertEquals(0, r.exitCode(), () -> args + ": " + r);
            // The nine methods and the two implicit constructors.
            assertTrue(r.out().contains("Verified methods: 11"), () -> args + ": " + r);
        }
    }

    /**
     * Loops whose exits constant conditions decide. Unfolded, the frontend translates returnOut's
     * while (true) to return 1, and the if (DEBUG) gives breaksOut's while (true) a second exit,
     * which makes it return 1 too.
     */
    private static final String LOOPS = """
            package p;

            import static org.strata.jverify.JVerify.check;

            class Loops {
                static final boolean DEBUG = false;

                static int returnOut() {
                    int i = 0;
                    while (true) {
                        i = i + 1;
                        if (i == 2 * 3) return i;
                    }
                }

                static void callsReturnOut() {
                    check(returnOut() == 6);
                }

                static int breaksOut() {
                    int i = 0;
                    while (true) {
                        i = i + 1;
                        if (DEBUG) {
                            i = -1;
                            break;
                        }
                        if (i == 6) break;
                    }
                    return i;
                }

                static void callsBreaksOut() {
                    check(breaksOut() == 6);
                }
            }
            """;

    @Test
    void noConstantFoldingAddsNoLoopExit(@TempDir Path tmp) throws Exception {
        var loops = write(tmp, "Loops.java", LOOPS).toString();
        for (var entry : List.of("p.Loops#callsReturnOut", "p.Loops#callsBreaksOut")) {
            var r = run("--no-constant-folding", "--interpret", "--entry", entry, loops);
            assertEquals(0, r.exitCode(), () -> entry + ": " + r);
        }
    }

    /**
     * Every option is JFE's, with JFE's arity, type and default, but
     * --no-constant-folding, and every JFE option is here but the two this
     * frontend does not implement: a new JFE option fails this until it is
     * added or listed.
     */
    @Test
    void optionsMatchTheSourceFrontend() throws Exception {
        var jfeCommand = Class.forName("org.strata.jverify.verifier.AppCommand").getDeclaredConstructor();
        jfeCommand.setAccessible(true);
        var jfe = options(new CommandLine(jfeCommand.newInstance()).getCommandSpec());
        var ours = options(new CommandLine(new Main.VerifyCommand(name -> null)).getCommandSpec());
        ours.keySet().removeAll(BYTECODE_FRONTEND_ONLY);

        var expected = new TreeSet<>(jfe.keySet());
        expected.removeAll(SOURCE_FRONTEND_ONLY);
        assertEquals(expected, ours.keySet());
        for (var name : ours.keySet()) {
            OptionSpec a = jfe.get(name);
            OptionSpec b = ours.get(name);
            assertEquals(a.arity(), b.arity(), name);
            assertEquals(a.type(), b.type(), name);
            assertEquals(a.defaultValue(), b.defaultValue(), name);
        }

        var jfeInputs = new CommandLine(jfeCommand.newInstance()).getCommandSpec().positionalParameters();
        var ourInputs = new CommandLine(new Main.VerifyCommand(name -> null)).getCommandSpec().positionalParameters();
        assertEquals(1, ourInputs.size());
        assertEquals(jfeInputs.getFirst().arity(), ourInputs.getFirst().arity());
        assertEquals(jfeInputs.getFirst().type(), ourInputs.getFirst().type());
    }

    private static Map<String, OptionSpec> options(CommandSpec spec) {
        var byName = new TreeMap<String, OptionSpec>();
        for (OptionSpec o : spec.options()) {
            byName.put(o.longestName(), o);
        }
        return byName;
    }
}
