
module com.aws.jverify.verifier {
    requires jdk.compiler;
    requires org.checkerframework.checker.qual;
    requires com.aws.jverify.common;
    requires info.picocli;
    requires com.aws.jverify;
    requires net.bytebuddy;
    requires java.instrument;
    requires net.bytebuddy.agent;
    requires com.amazon.ion;

    opens com.aws.jverify.verifier to info.picocli;
}
