generate-laurel-ast:
	rm -rf verifier/src/main/java/org/strata/jverify/laurel
	cd Strata && lake exe strata javaGen Laurel org.strata.jverify.laurel ../verifier/src/main/java
