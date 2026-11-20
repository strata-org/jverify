package org.strata.java.bytecode.frontend;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Records, when javac has finished, what it knows of every class outside the
 * JDK that the written classfiles mention, and of their supertypes: the classes
 * the program was compiled against, wherever javac found them. The JDK's are
 * left to reflection, which sees the same ones.
 */
final class TypeRecorder implements TaskListener {
    /**
     * A run of the characters a class name can hold. A descriptor or signature names a class as
     * {@code Lpkg/C;} or {@code Lpkg/C<}.
     */
    private static final Pattern NAME_CHARS = Pattern.compile("[^;<>.\\[]+");

    private final JavacTask task;
    private final Path outputDir;
    private final Map<String, TypeFacts> types = new HashMap<>();
    /** Thrown from the listener, it would pass for a javac crash. */
    private @Nullable IOException failure;

    TypeRecorder(JavacTask task, Path outputDir) {
        this.task = task;
        this.outputDir = outputDir;
    }

    Map<String, TypeFacts> types() throws IOException {
        if (failure != null) {
            throw failure;
        }
        // javac accepts a superclass cycle among classfiles the program only mentions; the
        // hierarchy walks would not terminate on it.
        for (String fqn : types.keySet()) {
            var chain = new HashSet<String>();
            for (String c = fqn; c != null && types.containsKey(c); c = types.get(c).superclass()) {
                if (!chain.add(c)) {
                    throw new SuperclassCycleException(c);
                }
            }
        }
        return Map.copyOf(types);
    }

    @Override
    public void finished(TaskEvent e) {
        if (e.getKind() != TaskEvent.Kind.COMPILATION) {
            return;
        }
        try (Stream<Path> walk = Files.walk(outputDir)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                var model = ClassFile.of().parse(file);
                var members = new HashMap<String, InnerClassInfo>();
                model.findAttribute(Attributes.innerClasses()).ifPresent(a -> a.classes().stream()
                        .filter(c -> c.outerClass().isPresent())
                        .forEach(c -> members.put(c.innerClass().asInternalName(), c)));
                for (var entry : model.constantPool()) {
                    String text = entry instanceof ClassEntry c ? c.asSymbol().descriptorString()
                            : entry instanceof Utf8Entry u ? u.stringValue() : null;
                    if (text == null) {
                        continue;
                    }
                    // The name starts after the run's first 'L': a pattern starting at any 'L' would
                    // rescan a string constant's long run from each of them.
                    var m = NAME_CHARS.matcher(text);
                    while (m.find()) {
                        int start = text.indexOf('L', m.start(), m.end()) + 1;
                        int end = m.end();
                        if (start > 0 && start < end && end < text.length()
                                && (text.charAt(end) == ';' || text.charAt(end) == '<')) {
                            TypeElement t = byInternalName(text.substring(start, end), members);
                            if (t != null) {
                                record(t);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            failure = ex;
        }
    }

    /**
     * The class whose internal name is {@code name}. javac finds a top-level class by its binary name,
     * even one with a '$' in it, but not a member class; it lists each member class a classfile mentions
     * in the classfile's InnerClasses attribute, {@code members} here.
     */
    private @Nullable TypeElement byInternalName(String name, Map<String, InnerClassInfo> members) {
        InnerClassInfo member = members.get(name);
        if (member == null) {
            return task.getElements().getTypeElement(name.replace('/', '.'));
        }
        TypeElement outer = byInternalName(member.outerClass().orElseThrow().asInternalName(), members);
        if (outer != null) {
            for (TypeElement t : ElementFilter.typesIn(outer.getEnclosedElements())) {
                if (t.getSimpleName().contentEquals(member.innerName().orElseThrow().stringValue())) {
                    return t;
                }
            }
        }
        return null;
    }

    private void record(TypeElement t) {
        String fqn = fqn(t);
        if (types.containsKey(fqn) || !task.getElements().getModuleOf(t).isUnnamed()) {
            return;
        }
        TypeElement superclass = t.getSuperclass() instanceof DeclaredType d ? (TypeElement) d.asElement() : null;
        var interfaces = t.getInterfaces().stream().map(i -> (TypeElement) ((DeclaredType) i).asElement()).toList();
        var methods = ElementFilter.methodsIn(t.getEnclosedElements()).stream()
                .map(m -> new TypeFacts.Method(m.getSimpleName().toString(),
                        m.getParameters().stream().map(p -> descriptor(p.asType())).toList().toString(),
                        !m.getModifiers().contains(Modifier.STATIC) && !m.getModifiers().contains(Modifier.PRIVATE),
                        m.getModifiers().contains(Modifier.ABSTRACT)))
                .toList();
        types.put(fqn, new TypeFacts(superclass == null ? null : fqn(superclass),
                interfaces.stream().map(this::fqn).toList(), t.getKind().isInterface(), methods));
        if (superclass != null) {
            record(superclass);
        }
        interfaces.forEach(this::record);
    }

    private String fqn(TypeElement t) {
        return task.getElements().getBinaryName(t).toString().replace('$', '.');
    }

    /**
     * The descriptor of {@code type}'s erasure, as the classfile has it; a class
     * missing from the classpath is an {@code ERROR} type that still has its name.
     */
    private String descriptor(TypeMirror type) {
        TypeMirror erased = task.getTypes().erasure(type);
        return switch (erased.getKind()) {
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case CHAR -> "C";
            case SHORT -> "S";
            case INT -> "I";
            case LONG -> "J";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case ARRAY -> "[" + descriptor(((ArrayType) erased).getComponentType());
            case DECLARED, ERROR -> "L" + task.getElements().getBinaryName(
                    (TypeElement) ((DeclaredType) erased).asElement()).toString().replace('.', '/') + ";";
            default -> throw new IllegalStateException("no descriptor for " + erased);
        };
    }
}
