/*
 *  Copyright 2016-2024 Qameta Software Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.qameta.allure.cucumber6jvm;

import io.cucumber.core.eventbus.EventBus;
import io.cucumber.core.feature.FeatureParser;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.options.CommandlineOptionsParser;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.resource.ClassLoaders;
import io.cucumber.core.runtime.FeaturePathFeatureSupplier;
import io.cucumber.core.runtime.Runtime;
import io.cucumber.core.runtime.TimeServiceEventBus;
import io.github.glytching.junit.extension.system.SystemProperty;
import io.github.glytching.junit.extension.system.SystemPropertyExtension;
import io.qameta.allure.Step;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.FixtureResult;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.Link;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.Stage;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.test.AllureFeatures;
import io.qameta.allure.test.AllureResults;
import io.qameta.allure.test.RunUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.qameta.allure.util.ResultsUtils.PACKAGE_LABEL_NAME;
import static io.qameta.allure.util.ResultsUtils.SUITE_LABEL_NAME;
import static io.qameta.allure.util.ResultsUtils.TEST_CLASS_LABEL_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;
import static org.junit.jupiter.api.parallel.Resources.SYSTEM_PROPERTIES;

/**
 * @author charlie (Dmitry Baev).
 */
class AllureCucumber6JvmTest {

    @AllureFeatures.Base
    @Test
    void shouldSetName() {
        final AllureResults results = runFeature("features/simple.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getName)
                .containsExactlyInAnyOrder("Add a to b");
    }

    @AllureFeatures.PassedTests
    @Test
    void shouldSetStatus() {
        final AllureResults results = runFeature("features/simple.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStatus)
                .containsExactlyInAnyOrder(Status.PASSED);
    }

    @AllureFeatures.FailedTests
    @Test
    void shouldSetFailedStatus() {
        final AllureResults results = runFeature("features/failed.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStatus)
                .containsExactlyInAnyOrder(Status.FAILED);
    }

    @AllureFeatures.FailedTests
    @Test
    void shouldSetStatusDetails() {
        final AllureResults results = runFeature("features/failed.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStatusDetails)
                .extracting(StatusDetails::getMessage)
                .containsExactlyInAnyOrder("expecting 15 to be equal to 123");
    }

    @AllureFeatures.BrokenTests
    @Test
    void shouldSetBrokenStatus() {
        final AllureResults results = runFeature("features/broken.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStatus)
                .containsExactlyInAnyOrder(Status.BROKEN);
    }

    @AllureFeatures.Stages
    @Test
    void shouldSetStage() {
        final AllureResults results = runFeature("features/simple.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStage)
                .containsExactlyInAnyOrder(Stage.FINISHED);
    }

    @AllureFeatures.Timings
    @Test
    void shouldSetStart() {
        final long before = Instant.now().toEpochMilli();
        final AllureResults results = runFeature("features/simple.feature");
        final long after = Instant.now().toEpochMilli();

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStart)
                .allMatch(v -> v >= before && v <= after);
    }

    @AllureFeatures.Timings
    @Test
    void shouldSetStop() {
        final long before = Instant.now().toEpochMilli();
        final AllureResults results = runFeature("features/simple.feature");
        final long after = Instant.now().toEpochMilli();

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStop)
                .allMatch(v -> v >= before && v <= after);
    }

    @AllureFeatures.FullName
    @Test
    void shouldSetFullName() {
        final AllureResults results = runFeature("features/simple.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getFullName)
                .containsExactlyInAnyOrder("src/test/resources/features/simple.feature:3");
    }

    @AllureFeatures.Descriptions
    @Test
    void shouldSetDescription() {
        final AllureResults results = runFeature("features/description.feature");

        final String expected = "This is description for current feature.\n"
                                + "It should appear on each scenario in report";

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getDescription)
                .containsExactlyInAnyOrder(
                        expected,
                        expected
                );
    }

    @AllureFeatures.Descriptions
    @Test
    void shouldSetScenarioDescription() {
        final AllureResults results = runFeature("features/scenario_description.feature");

        final String expected = "This is description for current feature.\n"
                                + "It should appear on each scenario in report";

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getDescription)
                .containsExactlyInAnyOrder(
                        expected + "\n    scenario description 1",
                        expected + "\n    scenario description 2"
                );
    }

    @AllureFeatures.Attachments
    @Test
    void shouldAddDataTableAttachment() {
        final AllureResults results = runFeature("features/datatable.feature");

        final List<Attachment> attachments = results.getTestResults().stream()
                .map(TestResult::getSteps)
                .flatMap(Collection::stream)
                .map(StepResult::getAttachments)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        assertThat(attachments)
                .extracting(Attachment::getName, Attachment::getType)
                .containsExactlyInAnyOrder(
                        tuple("Data table", "text/tab-separated-values")
                );

        final Attachment dataTableAttachment = attachments.iterator().next();
        final Map<String, byte[]> attachmentFiles = results.getAttachments();
        assertThat(attachmentFiles)
                .containsKeys(dataTableAttachment.getSource());

        final byte[] bytes = attachmentFiles.get(dataTableAttachment.getSource());
        final String attachmentContent = new String(bytes, StandardCharsets.UTF_8);

        assertThat(attachmentContent)
                .isEqualTo("""
                        name\tlogin\temail
                        Viktor\tclicman\tclicman@ya.ru
                        Viktor2\tclicman2\tclicman2@ya.ru
                        """
                );

    }

    @AllureFeatures.Attachments
    @Test
    void shouldAddAttachments() {
        final AllureResults results = runFeature("features/attachments.feature");

        final List<Attachment> attachments = results.getTestResults().stream()
                .map(TestResult::getSteps)
                .flatMap(Collection::stream)
                .map(StepResult::getAttachments)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        assertThat(attachments)
                .extracting(Attachment::getName, Attachment::getType)
                .containsExactlyInAnyOrder(
                        tuple("TextAttachment", "text/plain"),
                        tuple("ImageAttachment", "image/png")
                );

        final List<String> attachmentContents = results.getAttachments().values().stream()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .collect(Collectors.toList());

        assertThat(attachmentContents)
                .containsExactlyInAnyOrder("text attachment", "image attachment");
    }

    @AllureFeatures.Steps
    @Test
    void shouldAddBackgroundSteps() {
        final AllureResults results = runFeature("features/background.feature");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .hasSize(1)
                .flatExtracting(TestResult::getSteps)
                .extracting(StepResult::getName)
                .containsExactly(
                        "Given  cat is sad",
                        "And  cat is murmur",
                        "When  Pet the cat",
                        "Then  Cat is happy"
                );
    }

    @AllureFeatures.Parameters
    @Test
    void shouldAddParametersFromExamples() {
        final AllureResults results = runFeature("features/examples.feature");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .hasSize(2);

        assertThat(testResults.get(0).getParameters())
                .hasSize(3);

        assertThat(testResults.get(1).getParameters())
                .hasSize(3);

        assertThat(testResults)
                .flatExtracting(TestResult::getParameters)
                .extracting(Parameter::getName, Parameter::getValue)
                .containsExactlyInAnyOrder(
                        tuple("a", "1"), tuple("b", "3"), tuple("result", "4"),
                        tuple("a", "2"), tuple("b", "4"), tuple("result", "6")
                );

    }

    @AllureFeatures.Parameters
    @Test
    void shouldHandleMultipleExamplesPerOutline() {
        final AllureResults results = runFeature("features/multi-examples.feature");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .hasSize(2);

        assertThat(testResults)
                .flatExtracting(TestResult::getParameters)
                .extracting(Parameter::getName, Parameter::getValue)
                .containsExactlyInAnyOrder(
                        tuple("a", "1"), tuple("b", "3"), tuple("result", "4"),
                        tuple("a", "2"), tuple("b", "4"), tuple("result", "6")
                );
    }

    @AllureFeatures.Parameters
    @Test
    void shouldSupportTaggedExamplesBlocks() {
        final AllureResults results = runFeature("features/multi-examples.feature", "--tags", "@ExamplesTag2");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .hasSize(1);

        assertThat(testResults)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getName, Label::getValue)
                .contains(
                        tuple("tag", "ExamplesTag2")
                );

        assertThat(testResults)
                .flatExtracting(TestResult::getParameters)
                .extracting(Parameter::getName, Parameter::getValue)
                .containsExactlyInAnyOrder(
                        tuple("a", "2"), tuple("b", "4"), tuple("result", "6")
                );
    }

    @AllureFeatures.MarkerAnnotations
    @Test
    void shouldAddTags() {
        final AllureResults results = runFeature("features/tags.feature");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getName, Label::getValue)
                .contains(
                        tuple("tag", "FeatureTag"),
                        tuple("tag", "good")
                );
    }

    @AllureFeatures.Links
    @ExtendWith(SystemPropertyExtension.class)
    @SystemProperty(name = "allure.link.issue.pattern", value = "https://example.org/issue/{}")
    @SystemProperty(name = "allure.link.tms.pattern", value = "https://example.org/tms/{}")
    @Test
    void shouldAddLinks() {
        final AllureResults results = runFeature("features/tags.feature");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .flatExtracting(TestResult::getLinks)
                .extracting(Link::getName, Link::getType, Link::getUrl)
                .contains(
                        tuple("OAT-4444", "tms", "https://example.org/tms/OAT-4444"),
                        tuple("BUG-22400", "issue", "https://example.org/issue/BUG-22400")
                );
    }

    @AllureFeatures.MarkerAnnotations
    @Test
    void shouldAddBddLabels() {
        final AllureResults results = runFeature("features/tags.feature");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getName, Label::getValue)
                .contains(
                        tuple("feature", "Test Simple Scenarios"),
                        tuple("story", "Add a to b")
                );
    }

    @AllureFeatures.Timeline
    @Test
    void shouldAddThreadHostLabels() {
        final AllureResults results = runFeature("features/tags.feature");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getName)
                .contains("host", "thread");
    }

    @AllureFeatures.MarkerAnnotations
    @Test
    void shouldAddCommonLabels() {
        final AllureResults results = runFeature("features/tags.feature");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getName, Label::getValue)
                .contains(
                        tuple(PACKAGE_LABEL_NAME, "src.test.resources.features.tags_feature.Test Simple Scenarios"),
                        tuple(SUITE_LABEL_NAME, "Test Simple Scenarios"),
                        tuple(TEST_CLASS_LABEL_NAME, "Add a to b")
                );
    }

    @AllureFeatures.Steps
    @Test
    void shouldProcessUndefinedSteps() {
        final AllureResults results = runFeature("features/undefined.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStatus)
                .containsExactlyInAnyOrder(Status.SKIPPED);

        assertThat(testResults.get(0).getSteps())
                .extracting(StepResult::getName, StepResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Given  a is 5", Status.PASSED),
                        tuple("When  step is undefined", null),
                        tuple("Then  b is 10", Status.SKIPPED)
                );
    }

    @AllureFeatures.SkippedTests
    @AllureFeatures.Steps
    @Test
    void shouldProcessPendingExceptionsInSteps() {
        final AllureResults results = runFeature("features/pending.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getStatus)
                .containsExactlyInAnyOrder(Status.SKIPPED);

        assertThat(testResults.get(0).getSteps())
                .extracting(StepResult::getName, StepResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Given  a is 5", Status.PASSED),
                        tuple("When  step is yet to be implemented", Status.SKIPPED),
                        tuple("Then  b is 10", Status.SKIPPED)
                );
    }

    @AllureFeatures.Base
    @Test
    void shouldSupportDryRunForSimpleFeatures() {
        final AllureResults results = runFeature("features/simple.feature", "--dry-run");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getName, TestResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Add a to b", Status.PASSED)
                );

        assertThat(testResults.get(0).getSteps())
                .extracting(StepResult::getName, StepResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Given  a is 5", Status.PASSED),
                        tuple("And  b is 10", Status.PASSED),
                        tuple("When  I add a to b", Status.PASSED),
                        tuple("Then  result is 15", Status.PASSED)
                );
    }

    @AllureFeatures.Fixtures
    @AllureFeatures.Base
    @Test
    void shouldSupportDryRunForHooks() {
        final AllureResults results = runFeature("features/hooks.feature", "--dry-run", "-t",
                "@WithHooks or @BeforeHookWithException or @AfterHookWithException");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getName, TestResult::getStatus)
                .startsWith(
                        tuple("Simple scenario with Before and After hooks", Status.PASSED)
                );

        assertThat(results.getTestResultContainers().get(0).getBefores())
                .extracting(FixtureResult::getName, FixtureResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("io.qameta.allure.cucumber6jvm.samples.HookSteps.beforeHook()", Status.PASSED)
                );

        assertThat(results.getTestResultContainers().get(0).getAfters())
                .extracting(FixtureResult::getName, FixtureResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("io.qameta.allure.cucumber6jvm.samples.HookSteps.afterHook()", Status.PASSED)
                );

        assertThat(testResults.get(0).getSteps())
                .extracting(StepResult::getName, StepResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Given  a is 7", Status.PASSED),
                        tuple("And  b is 8", Status.PASSED),
                        tuple("When  I add a to b", Status.PASSED),
                        tuple("Then  result is 15", Status.PASSED)
                );
    }

    @AllureFeatures.History
    @Test
    void shouldPersistHistoryIdForScenarios() {
        final AllureResults results = runFeature("features/simple.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults.get(0).getHistoryId())
                .isEqualTo("892e5eabe51184301cf1358453c9f052");
    }

    @AllureFeatures.History
    @Test
    void shouldPersistHistoryIdForExamples() {
        final AllureResults results = runFeature("features/examples.feature", "--threads", "2");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getHistoryId)
                .containsExactlyInAnyOrder("c0f824814a130048e9f86358363cf23e", "646aca5d0775cd4f13161e1ea1a68c39");
    }

    @AllureFeatures.History
    @Test
    void shouldPersistDifferentHistoryIdComparedToTheSameTestCaseInDifferentLocation() {
        final AllureResults results1 = runFeature("features/simple.feature");
        final AllureResults results2 = runFeature("features/same/simple.feature");

        assertThat(results1.getTestResults().get(0).getHistoryId())
                .isNotEqualTo(results2.getTestResults().get(0).getHistoryId());
    }

    @AllureFeatures.Parallel
    @Test
    void shouldProcessScenariosInParallelMode() {
        final AllureResults results = runFeature("features/parallel.feature", "--threads", "3");

        final List<TestResult> testResults = results.getTestResults();

        assertThat(testResults)
                .hasSize(3);

        assertThat(testResults)
                .extracting(testResult -> testResult.getSteps().stream().map(StepResult::getName).collect(Collectors.toList()))
                .containsSubsequence(
                        Arrays.asList("Given  a is 1",
                                "And  b is 3",
                                "When  I add a to b",
                                "Then  result is 4")
                );

        assertThat(testResults)
                .extracting(testResult -> testResult.getSteps().stream().map(StepResult::getName).collect(Collectors.toList()))
                .containsSubsequence(
                        Arrays.asList("Given  a is 2",
                                "And  b is 4",
                                "When  I add a to b",
                                "Then  result is 6")
                );

        assertThat(testResults)
                .extracting(testResult -> testResult.getSteps().stream().map(StepResult::getName).collect(Collectors.toList()))
                .containsSubsequence(
                        Arrays.asList("Given  a is 7",
                                "And  b is 8",
                                "When  I add a to b",
                                "Then  result is 15")
                );
    }

    @AllureFeatures.Fixtures
    @AllureFeatures.Stages
    @Test
    void shouldDisplayHooksAsStages() {
        final AllureResults results = runFeature("features/hooks.feature", "-t",
                "@WithHooks or @BeforeHookWithException or @AfterHookWithException");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getName, TestResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Simple scenario with Before and After hooks", Status.PASSED),
                        tuple("Simple scenario with Before hook with Exception", Status.SKIPPED),
                        tuple("Simple scenario with After hook with Exception", Status.BROKEN)
                );

        assertThat(testResults.get(0).getSteps())
                .extracting(StepResult::getName, StepResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Given  a is 7", Status.PASSED),
                        tuple("And  b is 8", Status.PASSED),
                        tuple("When  I add a to b", Status.PASSED),
                        tuple("Then  result is 15", Status.PASSED)
                );


        assertThat(results.getTestResultContainers().get(0).getBefores())
                .extracting(FixtureResult::getName, FixtureResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("io.qameta.allure.cucumber6jvm.samples.HookSteps.beforeHook()", Status.PASSED)
                );

        assertThat(results.getTestResultContainers().get(0).getAfters())
                .extracting(FixtureResult::getName, FixtureResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("io.qameta.allure.cucumber6jvm.samples.HookSteps.afterHook()", Status.PASSED)
                );

        assertThat(testResults.get(1).getSteps())
                .extracting(StepResult::getName, StepResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Given  a is 7", Status.SKIPPED),
                        tuple("And  b is 8", Status.SKIPPED),
                        tuple("When  I add a to b", Status.SKIPPED),
                        tuple("Then  result is 15", Status.SKIPPED)
                );


        assertThat(results.getTestResultContainers().get(1).getBefores())
                .extracting(FixtureResult::getName, FixtureResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("io.qameta.allure.cucumber6jvm.samples.HookSteps.beforeHookWithException()", Status.FAILED)
                );


        assertThat(testResults.get(2).getSteps())
                .extracting(StepResult::getName, StepResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Given  a is 7", Status.PASSED),
                        tuple("And  b is 8", Status.PASSED),
                        tuple("When  I add a to b", Status.PASSED),
                        tuple("Then  result is 15", Status.PASSED)
                );

        assertThat(results.getTestResultContainers().get(2).getAfters())
                .extracting(FixtureResult::getName, FixtureResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("io.qameta.allure.cucumber6jvm.samples.HookSteps.afterHookWithException()", Status.FAILED)
                );

    }

    @AllureFeatures.BrokenTests
    @Test
    void shouldHandleAmbigiousStepsExceptions() {
        final AllureResults results = runFeature("features/ambigious.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .extracting(TestResult::getName, TestResult::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Simple scenario with ambigious steps", Status.SKIPPED)
                );

        assertThat(testResults.get(0).getSteps())
                .extracting(StepResult::getName, StepResult::getStatus)
                .containsExactly(
                        tuple("When  ambigious step present", null),
                        tuple("Then  something bad should happen", Status.SKIPPED)
                );
    }

    @ResourceLock(value = SYSTEM_PROPERTIES, mode = READ_WRITE)
    @SystemProperty(name = "allure.label.x-provided", value = "cucumberjvm6-test-provided")
    @Test
    void shouldSupportProvidedLabels() {
        final AllureResults results = runFeature("features/simple.feature");

        final List<TestResult> testResults = results.getTestResults();
        assertThat(testResults)
                .hasSize(1)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getName, Label::getValue)
                .contains(
                        tuple("x-provided", "cucumberjvm6-test-provided")
                );
    }

    @SystemProperty(name = "cucumber.junit-platform.naming-strategy", value = "long")
    @Step
    private AllureResults runFeature(final String featureResource,
                                     final String... moreOptions) {
        return RunUtils.runTests(lifecycle -> {
            final AllureCucumber6Jvm cucumber6jvm = new AllureCucumber6Jvm(lifecycle);
            final Supplier<ClassLoader> classLoader = ClassLoaders::getDefaultClassLoader;
            final List<String> opts = new ArrayList<>(Arrays.asList(
                    "--glue", "io.qameta.allure.cucumber6jvm.samples",
                    "--plugin", "null_summary"
            ));
            opts.addAll(Arrays.asList(moreOptions));
            final FeatureWithLines featureWithLines = FeatureWithLines.parse("src/test/resources/" + featureResource);
            final RuntimeOptions options = new CommandlineOptionsParser(System.out)
                    .parse(opts.toArray(new String[]{})).addFeature(featureWithLines).build();

            final EventBus bus = new TimeServiceEventBus(Clock.systemUTC(), UUID::randomUUID);
            final FeatureParser parser = new FeatureParser(bus::generateId);
            final FeaturePathFeatureSupplier supplier
                    = new FeaturePathFeatureSupplier(classLoader, options, parser);

            final Runtime runtime = Runtime.builder()
                    .withClassLoader(classLoader)
                    .withRuntimeOptions(options)
                    .withAdditionalPlugins(cucumber6jvm)
                    .withFeatureSupplier(supplier)
                    .build();

            runtime.run();
        });
    }
}
