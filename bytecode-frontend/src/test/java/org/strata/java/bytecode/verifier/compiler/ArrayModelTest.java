package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.CompositeType;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.TypeDefinition;
import org.strata.jverify.laurel.Variable;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The array model: element kinds, Laurel types, availability, shape check, and frames. No Strata. */
class ArrayModelTest {

    private static List<Path> classPath() {
        return List.of(Path.of(java.util.Objects.requireNonNull(
                System.getProperty("bytecode.library.jar"),
                "bytecode.library.jar system property not set")));
    }

    private static LibraryContracts objectOnly(ClassResolver resolver) {
        var contracts = LibraryContracts.of(LibraryContracts.objectContractSource(), List.of(), classPath());
        contracts.bind(resolver, Set.of(), new Reporter(), null);
        return contracts;
    }

    private static CompositeModel composites(LibraryContracts contracts, ClassResolver resolver) {
        return new CompositeModel(resolver, contracts);
    }

    private static CompositeModel withObjectContract() {
        var resolver = new ClassResolver(classPath(), true);
        return composites(objectOnly(resolver), resolver);
    }

    private static HighType jarray(HighType element) {
        return new HighType.Applied(LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident("JArray"))),
                List.of(LaurelFactory.node(element)));
    }

    private static String refusal(Runnable r) {
        return assertThrows(JavaViolationException.class, r::run).getMessage();
    }

    // --- Element kinds ---

    @Test
    void kindTable() {
        record Row(ArrayModel.ElementKind kind, String desc, HighType carrier, StmtExpr zero, String suffix,
                   String javaName, Long lo, Long hi) {
        }
        var rows = List.of(
                new Row(ArrayModel.ElementKind.INT8, "[B", new HighType.TInt(), new StmtExpr.LiteralInt(0),
                        "int8", "byte", -128L, 127L),
                new Row(ArrayModel.ElementKind.CHAR, "[C", new HighType.TInt(), new StmtExpr.LiteralInt(0),
                        "char", "char", 0L, 65535L),
                new Row(ArrayModel.ElementKind.INT16, "[S", new HighType.TInt(), new StmtExpr.LiteralInt(0),
                        "int16", "short", -32768L, 32767L),
                new Row(ArrayModel.ElementKind.INT32, "[I", new HighType.TInt(), new StmtExpr.LiteralInt(0),
                        "int32", "int", (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE),
                new Row(ArrayModel.ElementKind.INT64, "[J", new HighType.TInt(), new StmtExpr.LiteralInt(0),
                        "int64", "long", Long.MIN_VALUE, Long.MAX_VALUE),
                new Row(ArrayModel.ElementKind.BOOL, "[Z", new HighType.TBool(), new StmtExpr.LiteralBool(false),
                        null, "boolean", null, null));
        assertEquals(ArrayModel.ElementKind.values().length, rows.size());
        for (var row : rows) {
            var k = row.kind();
            assertEquals(row.desc(), k.descriptor(), k::name);
            assertEquals(row.desc().substring(1), k.elementDescriptor(), k::name);
            assertEquals(row.carrier(), k.carrier(), k::name);
            assertEquals(row.zero(), k.zero(), k::name);
            assertEquals(row.suffix(), k.helperSuffix(), k::name);
            assertEquals(row.suffix(), k.rangeType(), k::name);
            assertEquals(row.javaName(), k.javaName(), k::name);
            assertEquals(row.lo(), k.lo(), k::name);
            assertEquals(row.hi(), k.hi(), k::name);
            assertSame(k, ArrayModel.kindOfDescriptor(row.desc()), k::name);
        }
    }

    @Test
    void kindOfDescriptor() {
        assertEquals(ArrayModel.ElementKind.INT32, ArrayModel.kindOfDescriptor("[I"));
        assertEquals(ArrayModel.ElementKind.BOOL, ArrayModel.kindOfDescriptor("[Z"));
        assertNull(ArrayModel.kindOfDescriptor("[F"));
        assertNull(ArrayModel.kindOfDescriptor("[D"));
        assertNull(ArrayModel.kindOfDescriptor("[[I"));
        assertNull(ArrayModel.kindOfDescriptor("[Ljava/lang/String;"));
        assertNull(ArrayModel.kindOfDescriptor("I"));
        assertNull(ArrayModel.kindOfDescriptor(null));
    }

    // --- Laurel types ---

    @Test
    void laurelTypes() {
        var composites = withObjectContract();
        var arrays = composites.arrays;
        assertEquals(jarray(new HighType.TInt()), arrays.laurelType(TypeRef.ofDescriptor("[I")));
        assertEquals(jarray(new HighType.TInt()), arrays.laurelType(TypeRef.ofRendered("int[]", false, false)));
        assertEquals(jarray(new HighType.TBool()), arrays.laurelType(TypeRef.ofDescriptor("[Z")));
        assertEquals(jarray(new HighType.TInt()), arrays.laurelType(TypeRef.ofDescriptor("[B")));
        assertEquals(jarray(new HighType.TInt()), arrays.laurelType(TypeRef.ofRendered("long[]", false, false)));

        // First use declares the composite, its type parameter and its field.
        assertTrue(composites.referencedCompositeTypes.contains("JArray"));
        assertEquals(List.of("TArrayElement"), composites.compositeTypeParams.get("JArray"));
        var field = composites.compositeFields.get("JArray").get("contentsVersion");
        assertNotNull(field);
        assertTrue(field.isMutable());
        assertEquals(new HighType.TInt(), field.type().val());
        assertEquals(Set.of("contentsVersion"), composites.compositeFields.get("JArray").keySet());
    }

    @Test
    void unsupportedArrayTypesRefuse() {
        var arrays = withObjectContract().arrays;
        assertEquals("Unsupported type: float[]", refusal(() -> arrays.laurelType(TypeRef.ofDescriptor("[F"))));
        assertEquals("Unsupported type: double[]",
                refusal(() -> arrays.laurelType(TypeRef.ofRendered("double[]", false, false))));
        assertEquals(ArrayModel.REFERENCE,
                refusal(() -> arrays.laurelType(TypeRef.ofDescriptor("[Ljava/lang/String;"))));
        assertEquals(ArrayModel.REFERENCE,
                refusal(() -> arrays.laurelType(TypeRef.ofRendered("String[]", false, false))));
        assertEquals(ArrayModel.REFERENCE, refusal(() -> arrays.laurelType(TypeRef.ofRendered("T[]", false, false))));
        assertEquals(ArrayModel.MULTI, refusal(() -> arrays.laurelType(TypeRef.ofDescriptor("[[I"))));
        assertEquals(ArrayModel.MULTI, refusal(() -> arrays.laurelType(TypeRef.ofRendered("int[][]", false, false))));
        assertEquals("Unsupported type: int[]",
                refusal(() -> arrays.laurelType(TypeRef.ofDescriptor("[I", true, false))));
        assertEquals("Unsupported type: int[]",
                refusal(() -> arrays.laurelType(TypeRef.ofDescriptor("[I", false, true))));
        assertThrows(IllegalArgumentException.class, () -> ArrayModel.kindOfType(TypeRef.ofDescriptor("I")));
    }

    @Test
    void compositeEmission() {
        var composites = withObjectContract();
        composites.arrays.registerComposite();
        var out = new ArrayList<TypeDefinition>();
        composites.emitCompositeClosure("JArray", new HashSet<>(), out);
        var types = out.stream()
                .map(td -> assertInstanceOf(TypeDefinition.Composite.class, td).ty())
                .toList();
        assertEquals(List.of("Object", "JArray"), types.stream().map(c -> c.name().text()).toList());
        CompositeType object = types.get(0);
        assertEquals(List.of(), object.typeArgs());
        CompositeType jarray = types.get(1);
        assertEquals(List.of("TArrayElement"), jarray.typeArgs().stream().map(i -> i.text()).toList());
        assertEquals(List.of(LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident("Object")))),
                jarray.extending());
        assertEquals(1, jarray.fields().size());
        assertEquals("contentsVersion", jarray.fields().getFirst().name().text());
        assertEquals(new HighType.TInt(), jarray.fields().getFirst().type().val());
    }

    // --- Availability ---

    @Test
    void enabled() {
        var resolver = new ClassResolver(classPath(), true);
        var none = composites(LibraryContracts.none(), resolver).arrays;
        assertThrows(IllegalStateException.class, none::enabled);  // read before computed
        none.computeEnabled();
        assertFalse(none.enabled());

        var contracts = objectOnly(resolver);
        var arrays = composites(contracts, resolver).arrays;
        arrays.computeEnabled();
        assertTrue(arrays.enabled());
        assertFalse(contracts.loaded());           // computing enabled never loads

        resolver.programClassModels.put("p.Other", classModel("p.Other", cb -> { }));
        arrays.computeEnabled();
        assertTrue(arrays.enabled());
        resolver.programClassModels.put("p.Outer.JArray", classModel("p.Outer$JArray", cb -> { }));
        assertTrue(arrays.enabled());              // cached: unchanged until recomputed
        arrays.computeEnabled();
        assertFalse(arrays.enabled());             // a program class named JArray, nested too
    }

    @Test
    void members() {
        var resolver = new ClassResolver(classPath(), true);
        var arrays = composites(objectOnly(resolver), resolver).arrays;
        var get = arrays.member("get");
        assertEquals(ContractMember.Kind.ARRAY_MODEL, get.kind());
        assertEquals("JArray#get", get.calleeKey());
        assertEquals("JArray#set", arrays.member("set").calleeKey());
        assertEquals("JArray#length", arrays.member("length").calleeKey());
        assertEquals(ArrayModel.UNAVAILABLE, refusal(() -> arrays.member("clone")));

        var disabled = composites(LibraryContracts.none(), resolver).arrays;
        assertEquals(ArrayModel.UNAVAILABLE, refusal(() -> disabled.member("get")));
    }

    // --- Shape check ---

    private static ClassModel classModel(String name, Consumer<java.lang.classfile.ClassBuilder> body) {
        return ClassFile.of().parse(ClassFile.of().build(ClassDesc.of(name), body));
    }

    /** A JArray-shaped class: generic, one int field, the three instance members; {@code extra} adds more. */
    private static ClassModel jarrayShaped(String signature, Consumer<java.lang.classfile.ClassBuilder> extra) {
        return classModel("org.strata.jverify.builtin.JArray", cb -> {
            if (signature != null) {
                cb.with(SignatureAttribute.of(ClassSignature.parseFrom(signature)));
            }
            cb.withField("contentsVersion", ConstantDescs.CD_int, 0);
            cb.withMethod("length", MethodTypeDesc.ofDescriptor("()I"), ClassFile.ACC_PUBLIC, mb -> { });
            cb.withMethod("get", MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Object;"), ClassFile.ACC_PUBLIC,
                    mb -> { });
            cb.withMethod("set", MethodTypeDesc.ofDescriptor("(ILjava/lang/Object;)V"), ClassFile.ACC_PUBLIC,
                    mb -> { });
            extra.accept(cb);
        });
    }

    private static final String GENERIC = "<TArrayElement:Ljava/lang/Object;>Ljava/lang/Object;";

    @Test
    void shapeCheck() {
        var resolver = new ClassResolver(classPath(), true);
        var contracts = objectOnly(resolver);
        contracts.ensureLoaded();
        assertNull(ArrayModel.shapeProblem(contracts.classBytes("org/strata/jverify/builtin/JArray")));

        assertNull(ArrayModel.shapeProblem(jarrayShaped(GENERIC, cb -> { })));
        // A static field does not count as array state.
        assertNull(ArrayModel.shapeProblem(jarrayShaped(GENERIC,
                cb -> cb.withField("CACHE", ConstantDescs.CD_int, ClassFile.ACC_STATIC))));

        assertNotNull(ArrayModel.shapeProblem(null));
        assertNotNull(ArrayModel.shapeProblem(jarrayShaped(null, cb -> { })));
        assertNotNull(ArrayModel.shapeProblem(jarrayShaped(
                "<E:Ljava/lang/Object;>Ljava/lang/Object;", cb -> { })));
        assertNotNull(ArrayModel.shapeProblem(jarrayShaped(
                "<TArrayElement:Ljava/lang/Object;U:Ljava/lang/Object;>Ljava/lang/Object;", cb -> { })));
        assertNotNull(ArrayModel.shapeProblem(jarrayShaped(GENERIC,
                cb -> cb.withField("size", ConstantDescs.CD_int, 0))));
        assertNotNull(ArrayModel.shapeProblem(classModel("org.strata.jverify.builtin.JArray", cb -> {
            cb.with(SignatureAttribute.of(ClassSignature.parseFrom(GENERIC)));
            cb.withField("contentsVersion", ConstantDescs.CD_long, 0);
        })));
        assertNotNull(ArrayModel.shapeProblem(classModel("org.strata.jverify.builtin.JArray", cb -> {
            cb.with(SignatureAttribute.of(ClassSignature.parseFrom(GENERIC)));
            cb.withField("contentsVersion", ConstantDescs.CD_int, 0);
            cb.withMethod("length", MethodTypeDesc.ofDescriptor("()I"), ClassFile.ACC_PUBLIC, mb -> { });
            cb.withMethod("get", MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Object;"), ClassFile.ACC_PUBLIC,
                    mb -> { });
            // set is static here
            cb.withMethod("set", MethodTypeDesc.ofDescriptor("(ILjava/lang/Object;)V"),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, mb -> { });
        })));
    }

    // --- Frames ---

    @Test
    void frameTarget() {
        AstNode<StmtExpr> a = new AstNode<>(LaurelFactory.var_("a"), SourceMap.UNKNOWN_RANGE);
        var target = ArrayModel.frameTarget(a);
        var field = assertInstanceOf(Variable.Field.class,
                assertInstanceOf(StmtExpr.Var.class, target.val()).var_());
        assertSame(a, field.target());
        assertEquals("contentsVersion", field.fieldName().text());
        assertSame(a.source(), target.source());
    }
}
