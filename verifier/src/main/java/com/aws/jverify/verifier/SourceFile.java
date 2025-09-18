package com.aws.jverify.verifier;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;
import java.nio.file.Path;

public class SourceFile extends SimpleJavaFileObject {
    final String content;

    /**
     * Constructs a {@link SourceFile} with the given path and content.
     * <p>
     * <strong>NOTE:</strong>
     * If the path is derived from a real filesystem (and not a hard-coded test value, for example)
     * then you should instead use {@link SourceFile#SourceFile(Path, String)}
     * in order to ensure correct functionality across different operating systems.
     */
    public SourceFile(String path, String content) {
        super(URI.create("string://" + path), Kind.SOURCE);
        this.content = content;
    }

    /**
     * Constructs a {@link SourceFile} with the given path and content.
     */
    public SourceFile(Path path, String content) {
        super(path.toUri(), Kind.SOURCE);
        this.content = content;
    }

    @Override
    public String getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }
}
