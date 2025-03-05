package com.aws.jverify.verifier;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

class SourceFile extends SimpleJavaFileObject {
    final String content;

    SourceFile(String path, String content) {
        super(URI.create("string:///" + path), Kind.SOURCE);
        this.content = content;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }
}
