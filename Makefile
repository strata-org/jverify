generate-dafny-ast:
	./gradlew :javaTypesGenerator:run --args="../dafny/Source/Scripts/Syntax.cs-schema ../verifier/src/main/java/com/aws/jverify/generated"

test-generation-nodiff: generate-dafny-ast
	git diff --exit-code --ignore-cr-at-eol \
		|| { echo 'Consider running `make generate-dafny-ast`'; exit 1; }

generate-laurel-ast:
	cd Strata && lake exe strata javaGen ./Strata/Languages/Laurel/Grammar/LaurelGrammar.st com.aws.jverify.laurel ../verifier/src/main/java