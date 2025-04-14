
module com.aws.jverify.verifier {
    requires jdk.compiler;
    requires org.checkerframework.checker.qual;
    requires jdk.jshell;
    requires com.aws.jverify.common;
    requires java.desktop;
    requires info.picocli;
    requires com.aws.jverify;
    requires java.sql;

    opens com.aws.jverify.verifier to info.picocli;
}