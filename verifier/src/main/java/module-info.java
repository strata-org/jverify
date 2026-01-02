
module com.aws.jverify.verifier {
    requires jdk.compiler;
    requires org.checkerframework.checker.qual;
    requires jdk.jshell;
    requires com.aws.jverify.common;
    requires info.picocli;
    requires com.aws.jverify;
    requires java.sql;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires org.jgrapht.core;
    requires java.naming;
    requires net.bytebuddy;
    requires java.instrument;
    requires net.bytebuddy.agent;
    requires com.amazon.ion;

    opens com.aws.jverify.verifier to info.picocli;
    opens com.aws.jverify.verifier.compiler.simplifications to info.picocli;
    opens com.aws.jverify.verifier.compiler to info.picocli;
    opens com.aws.jverify.verifier.compiler.frontend to info.picocli;
    opens com.aws.jverify.verifier.dafny to info.picocli;
    opens com.aws.jverify.verifier.laurel to info.picocli;
    opens com.aws.jverify.verifier.compiler.generator.dafny to info.picocli;
}