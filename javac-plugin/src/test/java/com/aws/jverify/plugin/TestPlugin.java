package com.aws.jverify.plugin;

import com.aws.jverify.JVerify;
import com.aws.jverify.common.Common;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public class TestPlugin {

    @Test
    public void testASTModification() {
        // Create a source file to modify
        JavaFileObject source = JavaFileObjects.forSourceString(
                "com.example.ModifyMe",
                "package com.example;\n" +
                        """
import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

public class ModifyMe {
  void Bar() {
    precondition(false);
    postcondition(false);
    invariant(false);
  }
}"""
        );
        
        var jVerifyClass = URI.create(Common.getJarLocationForClass(JVerify.class));
                
        Compilation compilation = Compiler.javac()
                .withProcessors(new JVerifyProcessor())
                .withClasspath(List.of(new File(jVerifyClass)))
                .compile(source);

        CompilationSubject.assertThat(compilation).succeeded();
    }
}
