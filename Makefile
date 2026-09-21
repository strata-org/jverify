generate-laurel-ast:
	rm -rf verifier/src/main/java/org/strata/jverify/laurel
	laurelJavaGen org.strata.jverify.laurel verifier/src/main/java
