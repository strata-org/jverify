generate-dafny-ast:
	dafny resource Syntax.cs-schema verifier/build/Syntax.cs-schema
	./gradlew :javaTypesGenerator:run --args="../verifier/build/Syntax.cs-schema ../verifier/src/main/java/com/aws/jverify/generated"

test-generation-nodiff: generate-dafny-ast
	status="$$(git status --porcelain)" && test -z "$$status" \
		|| { echo "$$status"; echo 'Consider running `make generate-dafny-ast`'; exit 1; }
