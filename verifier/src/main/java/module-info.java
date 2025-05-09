
module com.aws.jverify.verifier {
    requires jdk.compiler;
    requires org.checkerframework.checker.qual;
    requires jdk.jshell;
    requires com.aws.jverify.common;
    requires java.desktop;
    requires info.picocli;
    requires com.aws.jverify;
    requires java.sql;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;

    opens com.aws.jverify.verifier to info.picocli;
    exports com.aws.jverify.verifier;
}