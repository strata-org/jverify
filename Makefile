generate-dafny-ast:
	./gradlew :javaTypesGenerator:run --args="../dafny/Source/Scripts/Syntax.cs-schema ../verifier/src/main/java/com/aws/jverify/generated"

test-generation-nodiff: generate-dafny-ast
	status="$$(git status --porcelain)" && test -z "$$status" \
		|| { echo "$$status"; echo 'Consider running `make generate-dafny-ast`'; exit 1; }
