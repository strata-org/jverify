generate-dafny-ast:
	./gradlew :javaTypesGenerator:run --args="../dafny/Source/Scripts/Syntax.cs-schema ../verifier/src/main/java/com/aws/jverify/generated"
  
test-generation-nodiff: generate-dafny-ast
	(git status --porcelain || (echo 'Consider running `make generate-dafny-ast`'; exit 1; ))