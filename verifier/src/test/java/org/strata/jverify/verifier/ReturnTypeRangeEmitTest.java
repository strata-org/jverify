package org.strata.jverify.verifier;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonList;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import org.junit.jupiter.api.Test;
import org.strata.jverify.testengine.JVerifyTestEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: the emitted Laurel Ion carries a real source range on a
 * method's return type and parameter types.
 *
 * <p>The type is translated from a resolved javac
 * {@link com.sun.tools.javac.code.Type}, which has no tree position; the
 * compiler must stamp the declared type tree's range ({@code method.restype}
 * for return types, {@code param.vartype} for parameters) onto the
 * {@code AstNode<HighType>} wrapping the type. Without that, the type node
 * serializes a null source, Strata reads it back as its "no location"
 * sentinel, and an implicit no-overflow failure on the result gets reported
 * at a whole-file fallback position instead of at the return type.
 *
 * <p>Serialization convention (new struct-based format): each {@code AstNode<T>}
 * serializes as {@code {val: ..., source: ...}} where {@code source} is either
 * a struct {@code {file: ..., range: {start: {byteIdx: N}, stop: {byteIdx: N}}}}
 * or null for "no location".
 */
public class ReturnTypeRangeEmitTest {

    @Test
    public void returnTypeAndParameterTypesCarryDeclaredTypeRange() throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                class Abs {
                    static int abs(int x) {
                        return x < 0 ? -x : x;
                    }
                }
                """;
        var sourceFile = new SourceFile(Path.of("Abs.java"), source);

        Path emittedLaurel = Files.createTempFile("emitted-laurel-range", ".ion");
        Files.deleteIfExists(emittedLaurel);
        try {
            var baseOptions = JVerifyTestEngine.getVerifierOptions(
                    JVerifyTestEngine.makeJVerifyTestAnnotation(1, 0), null);
            var emitOptions = EmitLaurelWorkflowTest.withEmit(
                    baseOptions, emittedLaurel, /* emitLaurelOnly */ true);
            var emitDriver = Driver.getDriver(emitOptions);
            var emitResult = emitDriver
                    .verifyJavaFiles(new java.util.ArrayList<>(List.of(sourceFile)));
            assertEquals(0, emitResult.exitCode(), "the emit-only run should succeed");

            IonSystem ion = IonSystemBuilder.standard().build();
            // `--emit-laurel` writes binary Ion, so read the raw bytes.
            IonValue emittedProgram = ion.singleValue(Files.readAllBytes(emittedLaurel));
            assertTrue(emittedProgram instanceof IonStruct,
                    "emitted program should be a struct");

            IonStruct program = (IonStruct) emittedProgram;
            IonList procedures = (IonList) program.get("staticProcedures");
            assertNotNull(procedures, "program should have staticProcedures");

            // Find the `abs` procedure (skip predefined type procedures)
            IonStruct absProcedure = null;
            for (IonValue proc : procedures) {
                IonStruct procStruct = (IonStruct) proc;
                IonStruct nameStruct = (IonStruct) procStruct.get("name");
                String procName = ((com.amazon.ion.IonText) nameStruct.get("text")).stringValue();
                if (procName.contains("abs")) {
                    absProcedure = procStruct;
                    break;
                }
            }
            assertNotNull(absProcedure, "should find the 'abs' procedure");

            // Check return type (outputs) has a source range
            IonList outputs = (IonList) absProcedure.get("outputs");
            assertNotNull(outputs, "abs procedure should have outputs");
            assertEquals(1, outputs.size(), "abs should have exactly one output");
            IonStruct outputParam = (IonStruct) outputs.get(0);
            IonStruct outputType = (IonStruct) outputParam.get("type");
            assertNotNull(outputType, "output parameter should have a type");
            IonValue outputSource = outputType.get("source");
            assertNotNull(outputSource, "output type should have a source field");
            assertFalse(outputSource.isNullValue(),
                    "the return type's source must not be null — it should carry "
                    + "the declared type tree's range");

            // Verify the range points to the `int` return-type token
            IonStruct outputSourceStruct = (IonStruct) outputSource;
            IonStruct outputRange = (IonStruct) outputSourceStruct.get("range");
            long returnTypeStart = ((com.amazon.ion.IonInt) ((IonStruct) outputRange.get("start"))
                    .get("byteIdx")).longValue();
            long returnTypeStop = ((com.amazon.ion.IonInt) ((IonStruct) outputRange.get("stop"))
                    .get("byteIdx")).longValue();
            int expectedStart = source.indexOf("static int abs") + "static ".length();
            int expectedStop = expectedStart + "int".length();
            assertEquals(expectedStart, returnTypeStart,
                    "the return type range should start at the `int` token");
            assertEquals(expectedStop, returnTypeStop,
                    "the return type range should stop after the `int` token");

            // Check parameter type (inputs) has a source range
            IonList inputs = (IonList) absProcedure.get("inputs");
            assertNotNull(inputs, "abs procedure should have inputs");
            assertEquals(1, inputs.size(), "abs should have exactly one input");
            IonStruct inputParam = (IonStruct) inputs.get(0);
            IonStruct inputType = (IonStruct) inputParam.get("type");
            assertNotNull(inputType, "input parameter should have a type");
            IonValue inputSource = inputType.get("source");
            assertNotNull(inputSource, "input type should have a source field");
            assertFalse(inputSource.isNullValue(),
                    "the parameter type's source must not be null — it should carry "
                    + "the declared type tree's range");

            // Verify the parameter type range points to the `int` token in `int x`
            IonStruct inputSourceStruct = (IonStruct) inputSource;
            IonStruct inputRange = (IonStruct) inputSourceStruct.get("range");
            long paramTypeStart = ((com.amazon.ion.IonInt) ((IonStruct) inputRange.get("start"))
                    .get("byteIdx")).longValue();
            long paramTypeStop = ((com.amazon.ion.IonInt) ((IonStruct) inputRange.get("stop"))
                    .get("byteIdx")).longValue();
            // The `int` in `int x` comes after `static int abs(`
            int expectedParamStart = source.indexOf("(int x)") + 1; // skip the '('
            int expectedParamStop = expectedParamStart + "int".length();
            assertEquals(expectedParamStart, paramTypeStart,
                    "the parameter type range should start at the `int` token in `int x`");
            assertEquals(expectedParamStop, paramTypeStop,
                    "the parameter type range should stop after the `int` token in `int x`");
        } finally {
            Files.deleteIfExists(emittedLaurel);
        }
    }
}
