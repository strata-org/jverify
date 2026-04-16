generate-laurel-ast:
	rm -rf verifier/src/main/java/com/aws/jverify/laurel
	cd Strata && lake exe strata javaGen Laurel com.aws.jverify.laurel ../verifier/src/main/java
