
module org.strata.jverify.verifier {
    requires jdk.compiler;
    requires org.checkerframework.checker.qual;
    requires org.strata.jverify.common;
    requires info.picocli;
    requires org.strata.jverify;
    requires net.bytebuddy;
    requires java.instrument;
    requires net.bytebuddy.agent;
    requires com.amazon.ion;

    opens org.strata.jverify.verifier to info.picocli;
}
