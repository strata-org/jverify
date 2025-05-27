generate-dafny-ast:
	dafny resource Syntax.cs-schema verifier/build/Syntax.cs-schema
	./gradlew :javaTypesGenerator:run --args="../verifier/build/Syntax.cs-schema ../verifier/src/main/java/com/aws/jverify/generated"

test-generation-nodiff: generate-dafny-ast
	status="$$(git status --porcelain)" && test -z "$$status" \
		|| { echo "$$status"; echo 'Consider running `make generate-dafny-ast`'; exit 1; }

install-and-build-dafny:
	git clone https://github.com/dafny-lang/dafny
	cd dafny; git checkout $(DAFNY_COMMIT)
	make -C dafny exe
	make -C dafny z3-$(Z3_PLATFORM)
