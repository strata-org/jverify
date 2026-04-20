package org.strata.jverify.verifier;

import org.strata.jverify.common.Common;
import com.sun.tools.javac.util.DefinedBy;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Represents an entry at a particular path within a jar file.
 * Similar to com.sun.tools.javac.file.PathFileObject.JarFileObject,
 * but that class doesn't override method such as openInputStream() correctly
 * so we can't use it for this purpose.
 */
public class JarFileEntry implements JavaFileObject {

    private final Path sourceJar;
    private final JarEntry jarEntry;
    private final URI uri;

    public JarFileEntry(Path sourceJar, JarEntry jarEntry) {
        this.sourceJar = sourceJar;
        this.jarEntry = jarEntry;
        this.uri = URI.create("jar:" + sourceJar.toUri() + "!/" + jarEntry.getName());
    }

    @Override @DefinedBy(DefinedBy.Api.COMPILER)
    public String getName() {
        // (Comment and implementation copied from PathFileObject.JarFileObject)
        // The use of ( ) to delimit the entry name is not ideal
        // but it does match earlier behavior
        return sourceJar + "(" + jarEntry + ")";
    }

    @Override
    public String toString() {
        return "JarFileObject[" + sourceJar + ":" + jarEntry + "]";
    }

    @Override
    public Kind getKind() {
        return Kind.SOURCE;
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        String baseName = simpleName + kind.extension;
        return kind.equals(getKind())
                && (baseName.equals(jarEntry.getName())
                || jarEntry.getName().endsWith("/" + baseName));
    }

    @Override
    public NestingKind getNestingKind() {
        return null;
    }

    @Override
    public Modifier getAccessLevel() {
        return null;
    }

    @Override
    public URI toUri() {
        return uri;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        try (var jarFile = new JarFile(sourceJar.toFile())) {
            return jarFile.getInputStream(jarEntry);
        }
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        CharSequence charContent = getCharContent(ignoreEncodingErrors);
        if (charContent == null)
            throw new UnsupportedOperationException();
        if (charContent instanceof CharBuffer buffer && buffer.hasArray()) {
            return new CharArrayReader(buffer.array());
        }
        return new StringReader(charContent.toString());
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return Common.getJarEntry(new JarFile(sourceJar.toFile()), jarEntry);
    }

    @Override
    public Writer openWriter() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public boolean delete() {
        throw new UnsupportedOperationException();
    }
}
