package org.strata.java.bytecode.frontend;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Signature;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless lookups over classfile entities shared by the driver and the
 * translator: class nesting, method classification, generic-signature
 * rendering, type annotations, and small instruction/descriptor predicates.
 */
public final class ClassfileQueries {

    private ClassfileQueries() {
    }

    /** The {@code InnerClasses} entries of a class (empty when absent). */
    public static List<InnerClassInfo> innerClassesOf(ClassModel cm) {
        return cm.findAttribute(Attributes.innerClasses())
                 .map(InnerClassesAttribute::classes).orElse(List.of());
    }

    /**
     * The internal name of the class a nested class is declared directly inside, or null if it is top-level.
     * A member class records its enclosing class in its {@code InnerClasses} entry; a local or anonymous
     * class does not (it is not a member), so its {@code EnclosingMethod} is used instead.
     */
    public static String directOuter(ClassModel nested, InnerClassInfo ic) {
        var outer = ic.outerClass();
        if (outer.isPresent()) return outer.get().asInternalName();
        if (nested == null) return null;
        return nested.findAttribute(Attributes.enclosingMethod())
                     .map(em -> em.enclosingClass().asInternalName()).orElse(null);
    }

    /** [bytecode] Type-use annotations (visible + invisible) recorded on an element. CLASS-retention
     *  type annotations (@Nat, @Unbounded, @Nullable) land in RuntimeInvisibleTypeAnnotations. */
    public static <T extends java.lang.classfile.AttributedElement> List<java.lang.classfile.TypeAnnotation> typeAnnotationsOn(T el) {
        List<java.lang.classfile.TypeAnnotation> out = new ArrayList<>();
        el.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
          .ifPresent(a -> out.addAll(a.annotations()));
        el.findAttribute(Attributes.runtimeInvisibleTypeAnnotations())
          .ifPresent(a -> out.addAll(a.annotations()));
        return out;
    }

    /** The descriptor of JVerify's {@code @Impure} type annotation. */
    private static final String IMPURE_DESCRIPTOR = "Lorg/strata/jverify/Impure;";

    /**
     * True if {@code a} is JVerify's {@code @Impure}. The match is on the exact
     * descriptor: {@code @Impure} relaxes the reference-equality gate, so an
     * unrelated annotation that happens to be named {@code Impure} must not.
     */
    public static boolean isImpure(java.lang.classfile.Annotation a) {
        return a.className().stringValue().equals(IMPURE_DESCRIPTOR);
    }

    /**
     * True if this method is a bridge: the erasure shim the compiler adds when a generic method is
     * implemented at a narrower type. It has no source form, and emitting it alongside the real method
     * produces two declarations that collide after erasure ({@code Integer get()} and {@code Object get()}).
     */
    public static boolean isBridge(MethodModel m) {
        return m.flags().has(AccessFlag.BRIDGE) || m.flags().has(AccessFlag.SYNTHETIC);
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

    /** True if the method's code contains an invokedynamic backed by java.lang.runtime.ObjectMethods. */
    public static boolean usesObjectMethods(MethodModel m) {
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

    /** Render a generic type signature into a Java type string (simple names). */
    public static String renderSig(Signature s) {
        return switch (s) {
            case Signature.BaseTypeSig b -> ClassDesc.ofDescriptor(String.valueOf(b.baseType())).displayName();
            case Signature.TypeVarSig tv -> tv.identifier();
            case Signature.ArrayTypeSig a -> renderSig(a.componentSignature()) + "[]";
            case Signature.ClassTypeSig c -> {
                String internal = c.className();
                String n = javaName(internal.substring(internal.lastIndexOf('/') + 1));
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
                b.append('.').append(s);
            } else {
                b.append('.').append(s);
            }
        }
        return b.toString();
    }

    /**
     * The innermost segment of an internal name, as a legal Java identifier.
     *
     * <p>The JVM numbers the nested classes that have no usable source name: {@code Outer$1} is anonymous,
     * while {@code Outer$1Local} is a LOCAL class whose real name follows the disambiguating digits. Taking
     * the segment verbatim yields {@code 1} or {@code 1Local}, neither of which parses (and {@code 1E} is
     * even read as a malformed float), so the digits are stripped to recover the local name and an
     * anonymous class is given a synthetic one.
     */
    public static String simple(String internal) {
        int i = Math.max(internal.lastIndexOf('/'), internal.lastIndexOf('$'));
        String seg = internal.substring(i + 1);
        if (seg.isEmpty() || !Character.isDigit(seg.charAt(0))) return seg;
        int d = 0;
        while (d < seg.length() && Character.isDigit(seg.charAt(d))) d++;
        String name = seg.substring(d);
        return name.isEmpty() ? "Anon" + seg.substring(0, d)     // Outer$1      -> Anon1
                              : name;                            // Outer$1Local -> Local
    }

    /** The internal name inside a class descriptor {@code Lpkg/Outer$Inner;}. */
    public static String innerName(ClassDesc d) {
        String s = d.descriptorString();
        return s.startsWith("L") ? s.substring(1, s.length() - 1) : s;
    }

    public static String primName(TypeKind tk) {
        return switch (tk) {
            case BOOLEAN -> "boolean"; case BYTE -> "byte"; case CHAR -> "char"; case SHORT -> "short";
            case INT -> "int"; case LONG -> "long"; case FLOAT -> "float"; case DOUBLE -> "double";
            default -> "Object";
        };
    }

    public static String convTarget(Opcode op) {
        return switch (op.name().charAt(2)) {
            case 'L' -> "long"; case 'I' -> "int"; case 'F' -> "float"; case 'D' -> "double";
            case 'B' -> "byte"; case 'C' -> "char"; case 'S' -> "short"; default -> "int";
        };
    }

    /** The JVM descriptor a conversion opcode produces, so the converted value carries its new type. */
    public static String convDesc(Opcode op) {
        return switch (op.name().charAt(2)) {
            case 'L' -> "J"; case 'I' -> "I"; case 'F' -> "F"; case 'D' -> "D";
            case 'B' -> "B"; case 'C' -> "C"; case 'S' -> "S"; default -> "I";
        };
    }

    /** True for the integral descriptors a recovered boolean could have been erased into. */
    static boolean isIntLike(String d) {
        return d.equals("I") || d.equals("B") || d.equals("C") || d.equals("S") || d.equals("J");
    }

    public static boolean isConditionalBranch(Opcode op) {
        return switch (op) {
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> true;
            default -> false;
        };
    }

    public static boolean isIntConst(CodeElement e, int want) {
        return e instanceof ConstantInstruction ci && ci.constantValue() instanceof Integer i && i == want;
    }

    public static boolean sameLabel(Label a, Label b) { return a == b || (a != null && a.equals(b)); }
}
