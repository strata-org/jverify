/**
 * contracts2jqwik — generates jqwik {@code @Property} test methods from
 * {@code @AsProperty} JVerify contracts as readable Java source files.
 *
 * <p>Run via {@link org.strata.jverify.contracts2jqwik.Main}: pass an input
 * {@code .java} file containing {@code @AsProperty} methods and an output path
 * (or directory). The tool emits a sibling {@code .java} file containing one
 * {@code @Property} method per input contract, conjoined with {@code &&}.
 * The emitted file is plain Java, intended to be checked into source control,
 * read in code review, and inspected by humans when a property test fails.</p>
 */
module org.strata.jverify.contracts2jqwik {
    requires org.strata.jverify;
    requires org.strata.jverify.common;
    requires com.github.javaparser.core;

    exports org.strata.jverify.contracts2jqwik;
}
