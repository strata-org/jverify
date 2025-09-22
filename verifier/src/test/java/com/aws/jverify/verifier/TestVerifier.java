package com.aws.jverify.verifier;

import com.aws.jverify.common.Common;
import com.aws.jverify.testengine.JVerifyTestEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.aws.jverify.testengine.JVerifyTestEngine.testMarkedSource;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestVerifier {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");
    
    @Test
    public void verifyB() throws IOException {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        System.setProperty("user.dir", "/Users/rwillems/SourceCode/billing/src/AWSCPAgreementsModel/src");
        var exitCode = command.execute(
                "main/java/com/amazonaws/billing/contracts/CrossServiceDiscountContract.java",
                "main/java/com/amazonaws/billing/contracts/ServiceSpecificDiscountContract.java",
                "main/java/com/amazonaws/billing/contracts/Contract.java",
                "main/java/com/amazonaws/billing/contracts/deserializers/ClauseDeserializer.java",
                "main/java/com/amazonaws/billing/contracts/deserializers/JsonTemporaryLiteralDeserializer.java",
                "main/java/com/amazonaws/billing/contracts/deserializers/ListDeserializer.java",
                "main/java/com/amazonaws/billing/contracts/deserializers/JsonArrayDeserializer.java",
                "main/java/com/amazonaws/billing/contracts/deserializers/JsonLiteralDeserializer.java",
                "main/java/com/amazonaws/billing/contracts/deserializers/BigDecimalDeserializer.java",
                "main/java/com/amazonaws/billing/contracts/utils/ReflectionUtils.java",
                "main/java/com/amazonaws/billing/contracts/ContractSerializer.java",
                "main/java/com/amazonaws/billing/contracts/serializers/JsonArraySerializer.java",
                "main/java/com/amazonaws/billing/contracts/serializers/JsonTemporaryLiteralSerializer.java",
                "main/java/com/amazonaws/billing/contracts/serializers/JsonTraitSerializer.java",
                "main/java/com/amazonaws/billing/contracts/serializers/MapSerializer.java",
                "main/java/com/amazonaws/billing/contracts/serializers/RemoveLeadingUnderscoreStrategy.java",
                "main/java/com/amazonaws/billing/contracts/serializers/BigDecimalSerializer.java",
                "main/java/com/amazonaws/billing/contracts/serializers/SetSerializer.java",
                "main/java/com/amazonaws/billing/contracts/serializers/DafnySequenceSerializer.java",
                "main/java/com/amazonaws/billing/interop/Instant.java",
                "main/java/com/amazonaws/billing/interop/LocalDate.java",
                "main/java/com/amazonaws/billing/Terms/TrackingEntityTerm/TrackingEntity.java",
                "main/java/com/amazonaws/billing/Terms/TrackingEntityTerm/TrackingUnit.java",
                "main/java/com/amazonaws/billing/Terms/TrackingEntityTerm/TrackingUnitType.java",
                "main/java/com/amazonaws/billing/Terms/TrackingEntityTerm/TrackingEntityTerm.java",
                "main/java/com/amazonaws/billing/Terms/PayerAccountsTerm/PayerAccountsOrReference.java",
                "main/java/com/amazonaws/billing/Terms/PayerAccountsTerm/PayerAccountsTerm.java",
                "main/java/com/amazonaws/billing/Terms/AgreementPeriodTerm.java",
                "main/java/com/amazonaws/billing/Terms/DocumentValidityTerm.java",
                "main/java/com/amazonaws/billing/Terms/SignatoriesTerm.java",
                "main/java/com/amazonaws/billing/Terms/DiscountTerm/DiscountTerm.java",
                "main/java/com/amazonaws/billing/Terms/DiscountTerm/DiscountOrigin.java",
                "main/java/com/amazonaws/billing/Terms/DiscountTerm/OriginalContractTerm.java",
                "main/java/com/amazonaws/billing/Terms/DiscountTerm/DiscountTermPeriod.java",
                "main/java/com/amazonaws/billing/Terms/DiscountTerm/DiscountTermType.java",
                "main/java/com/amazonaws/billing/Terms/DiscountTerm/IntervalValue.java",
                "main/java/com/amazonaws/billing/Terms/DiscountTerm/PublicPricingProtection.java",
                "main/java/com/amazonaws/billing/Terms/IntervalTerm.java",
                "main/java/com/amazonaws/billing/Terms/ContractYearsTerm.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/RateUnitIncrement.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/RatesTerm.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/RateGroup.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/RateUnitValue.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/RateUnit.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/CompoundUnitItem.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/Rate.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/RateValue.java",
                "main/java/com/amazonaws/billing/Terms/RatesTerm/AttributeBasedUnit.java",
                "main/java/com/amazonaws/billing/Terms/Term/DefinesTopLevelProperties.java",
                "main/java/com/amazonaws/billing/Terms/Term/Addendum.java",
                "main/java/com/amazonaws/billing/Terms/Term/TermType.java",
                "main/java/com/amazonaws/billing/Terms/Term/Term.java",
                "main/java/com/amazonaws/billing/Terms/Term/Version.java",
                "main/java/com/amazonaws/billing/Terms/AttributeTerm/AttributeId.java",
                "main/java/com/amazonaws/billing/Terms/AttributeTerm/AttributeTerm.java",
                "main/java/com/amazonaws/billing/Terms/AttributeTerm/AttributeValue.java",
                "main/java/com/amazonaws/billing/Terms/AttributeTerm/AttributeName.java",
                "main/java/com/amazonaws/billing/Terms/AttributeTerm/AttributeValueType.java",
                "main/java/com/amazonaws/billing/Terms/DefinitionTerm.java",
                "main/java/com/amazonaws/billing/Terms/TrackingTerm/TrackingTerm.java",
                "main/java/com/amazonaws/billing/Terms/TrackingTerm/Aggregation.java",
                "main/java/com/amazonaws/billing/Terms/TrackingTerm/Usage.java",
                "main/java/com/amazonaws/billing/Terms/TrackingTerm/Period.java",
                "main/java/com/amazonaws/billing/Terms/RegionListTerm.java",
                "main/java/com/amazonaws/billing/Terms/AWSAccountTerm.java",
                "main/java/com/amazonaws/billing/Terms/MultipleIssueCreditTerm.java",
                "main/java/com/amazonaws/billing/Terms/CommitmentTerm/PeriodType.java",
                "main/java/com/amazonaws/billing/Terms/CommitmentTerm/CommitmentUnits.java",
                "main/java/com/amazonaws/billing/Terms/CommitmentTerm/CommitmentTerm.java",
                "main/java/com/amazonaws/billing/Terms/CommitmentTerm/CommitmentType.java",
                "main/java/com/amazonaws/billing/Terms/CommitmentTerm/RollOverExcess.java",
                "main/java/com/amazonaws/billing/Terms/AttributeRulesetTerm/AttributeRulesetTerm.java",
                "main/java/com/amazonaws/billing/Terms/AttributeRulesetTerm/AttributeLogicExpression.java",
                "main/java/com/amazonaws/billing/Terms/AttributeRulesetTerm/AttributeLogicExpression_Attribute.java",
                "main/java/com/amazonaws/billing/Terms/AttributeRulesetTerm/AttributeLogicExpression_Or.java",
                "main/java/com/amazonaws/billing/Terms/AttributeRulesetTerm/AttributeLogicExpression_Not.java",
                "main/java/com/amazonaws/billing/Terms/AttributeRulesetTerm/AttributeLogicExpression_And.java",
                "main/java/com/amazonaws/billing/Terms/PercentageDiscountTerm/ProductsAndRegions.java",
                "main/java/com/amazonaws/billing/Terms/PercentageDiscountTerm/PercentageDiscountTerm.java",
                "main/java/com/amazonaws/billing/Terms/PercentageDiscountTerm/Usage.java",
                "main/java/com/amazonaws/billing/Terms/ProductListTerm.java",
                "main/java/com/amazonaws/billing/agreements/ServiceSpecificRate.java",
                "main/java/com/amazonaws/billing/agreements/AgreementSerializer.java",
                "main/java/com/amazonaws/billing/agreements/CrossServiceDiscount.java",
                "main/java/com/amazonaws/billing/agreements/CrossServiceDiscountAgreement.java",
                "main/java/com/amazonaws/billing/agreements/BillingConcept.java",
                "main/java/com/amazonaws/billing/agreements/ServiceSpecificDiscountConcept.java",
                "main/java/com/amazonaws/billing/agreements/Discount.java",
                "main/java/com/amazonaws/billing/agreements/ServiceSpecificRateAgreement.java",
                "main/java/com/amazonaws/billing/agreements/Agreement.java",
                "main/java/com/amazonaws/billing/agreements/ServiceSpecificDiscountAgreement.java",
                "main/java/com/amazonaws/billing/Clauses/ServiceSpecificRatesClause/RegionRate.java",
                "main/java/com/amazonaws/billing/Clauses/ServiceSpecificRatesClause/ServiceSpecificRatesClause.java",
                "main/java/com/amazonaws/billing/Clauses/ServiceSpecificRatesClause/ServiceRate.java",
                "main/java/com/amazonaws/billing/Clauses/ServiceSpecificRatesClause/Increment.java",
                "main/java/com/amazonaws/billing/Clauses/ServiceSpecificRatesClause/UsageType.java",
                "main/java/com/amazonaws/billing/Clauses/SpendCommitmentClause/SpendCommitmentClause.java",
                "main/java/com/amazonaws/billing/Clauses/SpendCommitmentClause/SingleSpendCommitment.java",
                "main/java/com/amazonaws/billing/Clauses/CrossServiceDiscountClause.java",
                "main/java/com/amazonaws/billing/Clauses/CSDClause.java",
                "main/java/com/amazonaws/billing/Clauses/SingleContractYear.java",
                "main/java/com/amazonaws/billing/Clauses/ContractYearClause.java",
                "main/java/com/amazonaws/billing/Clauses/EligibleServicesClause.java",
                "main/java/com/amazonaws/billing/Clauses/DiscountTermClause.java",
                "main/java/com/amazonaws/billing/Clauses/EligiblePayerAccountsClause.java",
                "main/java/com/amazonaws/billing/Clauses/Clause.java",
                "main/java/com/amazonaws/billing/Clauses/SingleCrossServiceDiscount.java",
                "main/java/com/amazonaws/billing/Primitives/TextExtract.java",
                "main/java/com/amazonaws/billing/Primitives/RatePrimitive.java",
                "main/java/com/amazonaws/billing/Primitives/Either/Either.java",
                "main/java/com/amazonaws/billing/Primitives/Region.java",
                "main/java/com/amazonaws/billing/Primitives/Label.java",
                "main/java/com/amazonaws/billing/Primitives/Product.java",
                "main/java/com/amazonaws/billing/Primitives/Unit.java",
                "main/java/com/amazonaws/billing/Primitives/Interval.java",
                "main/java/com/amazonaws/billing/Primitives/LocalDatePrimitive.java",
                "main/java/com/amazonaws/billing/Primitives/ListPrimitive.java",
                "main/java/com/amazonaws/billing/Primitives/ChargeType.java",
                "main/java/com/amazonaws/billing/Primitives/ProductGroup.java",
                "main/java/com/amazonaws/billing/Primitives/InstantPrimitive.java",
                "main/java/com/amazonaws/billing/Primitives/HttpsLink.java",
                "main/java/com/amazonaws/billing/Primitives/IntegerPrimitive.java",
                "main/java/com/amazonaws/billing/Primitives/Valid.java",
                "main/java/com/amazonaws/billing/Primitives/ServiceSpecificRatePrimitive.java",
                "main/java/com/amazonaws/billing/Primitives/AWSAccount.java",
                "main/java/com/amazonaws/billing/Primitives/Json/JsonArray.java",
                "main/java/com/amazonaws/billing/Primitives/Json/Json.java",
                "main/java/com/amazonaws/billing/Primitives/Json/JsonObject.java",
                "main/java/com/amazonaws/billing/Primitives/Json/Literal.java",
                "main/java/com/amazonaws/billing/Primitives/Json/TemporaryLiteral.java",
                "main/java/com/amazonaws/billing/Primitives/Primitive.java",
                "main/java/com/amazonaws/billing/Primitives/Signatory/Proposer.java",
                "main/java/com/amazonaws/billing/Primitives/Signatory/Acceptor.java",
                "main/java/com/amazonaws/billing/Primitives/Service.java",
                "main/java/com/amazonaws/billing/Primitives/ARN.java",
                "main/java/com/amazonaws/billing/Primitives/Currency.java",
                "main/java/com/amazonaws/billing/Primitives/AddendumAgreementType.java",
                "main/java/com/amazonaws/billing/Primitives/Percentage.java",
                "main/java/com/amazonaws/billing/Primitives/RationalPrimitive.java",
                "main/java/com/amazonaws/billing/Primitives/MonetaryAmount.java",
                "main/java/com/amazonaws/billing/Primitives/SetPrimitive.java",
                "main/java/com/amazonaws/billing/Primitives/DocuSignId.java",
                "main/java/com/amazonaws/billing/Primitives/ProductDescription.java",
                "main/java/com/amazonaws/billing/Helpers/RuleBlox/defaultt.java",
                "--jar",
                "/Users/rwillems/brazil-pkg-cache/packages/Jackson-databind/Jackson-databind-2.12.x.416784.0/AL2_x86_64/DEV.STD.PTHREAD/build/lib/jackson-databind-2.12.7.1.jar:/Users/rwillems/brazil-pkg-cache/packages/Jackson-annotations/Jackson-annotations-2.12.x.400243.0/AL2_x86_64/DEV.STD.PTHREAD/build/lib/jackson-annotations-2.12.7.jar:/Users/rwillems/brazil-pkg-cache/packages/Jackson-core/Jackson-core-2.12.x.397949.0/AL2_x86_64/DEV.STD.PTHREAD/build/lib/jackson-core-2.12.7.jar:/Users/rwillems/brazil-pkg-cache/packages/Maven-org-dafny_DafnyRuntime/Maven-org-dafny_DafnyRuntime-4.9.1.0.0/AL2_x86_64/DEV.STD.PTHREAD/build/lib/DafnyRuntime-4.9.1.jar:/Users/rwillems/brazil-pkg-cache/packages/Z3Prover/Z3Prover-z3_4.13.0.75.0/AL2_x86_64/DEV.STD.PTHREAD/build/lib/com.microsoft.z3.jar",
                //"./test/java/com/amazonaws/billing/contracts/ContractsSerializerHelper.java",
                //"./test/java/com/amazonaws/billing/contracts/ContractSerializerTest.java",
                "--dafny=" + dafnyPath,
                "--print-dafny=" + "/Users/rwillems/SourceCode/GradleBased/build/temp2.dfy");
        Files.writeString(Path.of("/Users/rwillems/SourceCode/GradleBased/build/out.text"), out.toString());
        Assertions.assertEquals(2, exitCode, out.toString());
    }
    
    @Test
    public void verifyFibonacci() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();
        
        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("../examples/src/test/java/com/aws/jverify/examples/Fibonacci.java").toString(),
                "--dafny=" + dafnyPath);
        Assertions.assertEquals(0, exitCode, out.getBuffer().toString());
    }

    @Test
    public void verifyBinarySearch() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("../examples/src/test/java/com/aws/jverify/examples/BinarySearch.java").toString(),
                "--dafny=" + dafnyPath,
                "--print-dafny=../build/temp.dfy");
        Assertions.assertEquals(0, exitCode, out.getBuffer().toString());
    }

    @Test
    public void checkPathsOutput() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("./src/test/resources/AssertFalse.java").toString(),
                "--dafny=" + dafnyPath, "--paths");
        Assertions.assertTrue(out.toString().startsWith("src/test/resources/AssertFalse.java"), out.toString());
        Assertions.assertEquals(4, exitCode);
    }

    @Test
    public void verifyBuiltinContracts() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("./src/main/resources/builtin-contracts.java").toString(),
                "--dafny=" + dafnyPath,
                "--print-dafny=../build/temp.dfy",
                "--builtin-contracts=false");
        Assertions.assertEquals(0, exitCode, out.toString());
    }
    
    @Test
    public void javaError() throws IOException {
        var source = Common.getResourceFile(getClass(), "/JavaError.java");
        var annotation = JVerifyTestEngine.makeJVerifyTestAnnotation(true, 2, -1, -1, false, false, true);
        testMarkedSource(new SourceFile("JavaError.java", source), annotation);
    }

    @Test
    public void testRunThroughGradle() throws IOException, InterruptedException {
        var gradlePath = IS_WINDOWS ? "../gradlew.bat" : "../gradlew";
        ProcessBuilder processBuilder = new ProcessBuilder(
                gradlePath,
                ":verifier:run",
                "--args=\"../examples/src/test/java/com/aws/jverify/examples/Fibonacci.java\"");
        processBuilder.redirectErrorStream(true);
        var process = processBuilder.start();
        var writer = new StringWriter();
        int exitCode;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.transferTo(writer);
            exitCode = process.waitFor();
        }
        var output = canonicalizeNewlines(writer.toString());
        assertThat(output, containsString("Dafny program verifier finished with 6 verified, 0 errors"));
        Assertions.assertEquals(0, exitCode);
    }

    /**
     * Returns the text with all CRLF sequences replaced with LF.
     * This prevents erroneous failures of diff-based assertions on Windows platforms.
     */
    private static String canonicalizeNewlines(final String text) {
        return text.replaceAll("\r\n", "\n");
    }
}