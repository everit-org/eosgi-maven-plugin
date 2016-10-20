/*
 * Copyright (C) 2011 Everit Kft. (http://everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.osgi.dev.maven;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.everit.osgi.dev.dist.util.DistConstants;
import org.everit.osgi.dev.dist.util.attach.EOSGiVMManager;
import org.everit.osgi.dev.dist.util.configuration.LaunchConfigurationDTO;
import org.everit.osgi.dev.dist.util.configuration.schema.EnvironmentType;
import org.everit.osgi.dev.dist.util.configuration.schema.UseByType;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.dto.DistributedEnvironmentData;
import org.everit.osgi.dev.maven.util.DaemonStreamRedirector;
import org.everit.osgi.dev.maven.util.PluginUtil;
import org.everit.osgi.dev.testrunner.TestRunnerConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Runs the integration-tests on OSGi environment. It is necessary to add
 * <i>org.everit.osgi.dev.testrunner</i> and one of the engines as a dependency to the project to
 * make this goal work.
 */
@Mojo(name = "integration-test", defaultPhase = LifecyclePhase.INTEGRATION_TEST,
    requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST)
public class IntegrationTestMojo extends DistMojo {

  /**
   * A shutdown hook that stops the started OSGi container.
   */
  private class ShutdownHook extends Thread {

    private final Process process;

    private final int shutdownTimeout;

    private final String uniqueLaunchId;

    ShutdownHook(final Process process, final String uniqueLaunchId, final int shutdownTimeout) {
      this.process = process;
      this.uniqueLaunchId = uniqueLaunchId;
      this.shutdownTimeout = shutdownTimeout;
    }

    @Override
    public void run() {
      shutdownProcess(process, uniqueLaunchId, shutdownTimeout, 0);
    }
  }

  /**
   * The most simple implementation of an output stream that redirects all writings to a writer.
   */
  private static final class SimpleWriterOutputStream extends OutputStream {

    private final OutputStream outputStream;

    SimpleWriterOutputStream(final OutputStream outputStream) {
      this.outputStream = outputStream;
    }

    @Override
    public void close() {
    }

    @Override
    public void write(final int b) throws IOException {
      outputStream.write(b);
    }
  }

  /**
   * Struct of test results.
   */
  private static class TestResult {

    private int error;

    private int failure;

    private int skipped;

    private int tests;

    private void addToSum(final TestResult testResult) {
      tests += testResult.tests;
      error += testResult.error;
      failure += testResult.failure;
      skipped += testResult.skipped;
    }

  }

  public static final int DEFAULT_TEST_RUNNING_TIMEOUT = 180000;

  private static final long LOGGING_INTERVAL = 5000;

  private static final int MILLISECOND_NUM_IN_SECOND = 1000;

  private static final int TIMEOUT_CHECK_INTERVAL = 10;

  private static int convertTestSuiteAttributeToInt(final Element element, final String attribute,
      final File resultFile) throws MojoFailureException {
    String stringValue = element.getAttribute(attribute);
    if ("".equals(stringValue.trim())) {
      throw new MojoFailureException(
          "Invalid test result file " + resultFile.getAbsolutePath()
              + ". The attribute " + attribute + " in testSuite is not defined.");
    }

    try {
      return Integer.parseInt(stringValue);
    } catch (NumberFormatException e) {
      throw new MojoFailureException(
          "Invalid test result file " + resultFile.getAbsolutePath()
              + ". The attribute " + attribute + " is invalid.");
    }
  }

  /**
   * Whether to log the output of the started test JVMs to the standard output and standard error or
   * not.
   */
  @Parameter(property = "eosgi.consoleLog", defaultValue = "true")
  protected boolean consoleLog = true;

  /**
   * Skipping the integration tests, only execute the dist goal.
   */
  @Parameter(property = DistConstants.PLUGIN_PROPERTY_DIST_ONLY, defaultValue = "false")
  protected boolean distOnly = false;

  /**
   * The folder where the integration test reports will be placed. Please note that the content of
   * this folder will be deleted before running the tests.
   */
  @Parameter(property = "eosgi.integration-test.targetFolder",
      defaultValue = "${project.build.directory}/eosgi/integration-test")
  protected String integrationTestTargetFolder;

  /**
   * Skipping this plugin.
   */
  @Parameter(property = "eosgi.test.skip", defaultValue = "false")
  protected boolean skipTests = false;

  private void checkExitCode(final Process process, final String environmentId)
      throws MojoExecutionException {
    int exitCode = process.exitValue();
    if (exitCode != 0) {
      throw new MojoExecutionException("Test Process of environment " + environmentId
          + " finished with exit code " + exitCode);
    }
  }

  private void checkExitError(final File resultFolder, final String environmentId)
      throws MojoFailureException {
    File exitErrorFile = new File(resultFolder, TestRunnerConstants.SYSTEM_EXIT_ERROR_FILE_NAME);
    if (exitErrorFile.exists()) {
      StringBuilder sb = new StringBuilder();

      try (FileInputStream fin = new FileInputStream(exitErrorFile)) {
        InputStreamReader reader = new InputStreamReader(fin, Charset.defaultCharset());
        BufferedReader br = new BufferedReader(reader);
        String line = br.readLine();
        while (line != null) {
          sb.append(line).append("\n");
          line = br.readLine();
        }
      } catch (FileNotFoundException e) {
        getLog().error("Could not find file " + exitErrorFile.getAbsolutePath(), e);
      } catch (IOException e) {
        getLog().error("Error during reading exit error file " + exitErrorFile.getAbsolutePath(),
            e);
      }
      getLog().error(
          "Error during stopping the JVM of the environment " + environmentId
              + ". Information can be found at " + exitErrorFile.getAbsolutePath()
              + ". Content of the file is: \n" + sb.toString());

      throw new MojoFailureException(
          "Could not shut down the JVM of the environment " + environmentId
              + " in a nice way. For more information, see the content of the file: "
              + exitErrorFile.getAbsolutePath());

    }
  }

  private ProcessBuilder createTestProcessBuilder(final String environmentId,
      final File workingDirFile, final String[] command, final File testResultFolder) {
    String title = "EOSGi TestProcess - " + environmentId;

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
    processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);

    processBuilder.directory(workingDirFile);
    getLog().info("[" + title + "] Working dir: " + workingDirFile);

    Map<String, String> envMap = new HashMap<>(System.getenv());
    envMap.put(TestRunnerConstants.ENV_STOP_AFTER_TESTS, Boolean.TRUE.toString());
    envMap.put(TestRunnerConstants.ENV_TEST_RESULT_FOLDER, testResultFolder.getAbsolutePath());

    processBuilder.environment().putAll(envMap);

    getLog().info("[" + title + "] Environment: " + processBuilder.environment());

    return processBuilder;
  }

  private void defineStandardOutputs(final File stdOutFile, final List<OutputStream> stdOuts)
      throws MojoExecutionException {
    FileOutputStream stdOutFileOut;
    try {
      stdOutFileOut = new FileOutputStream(stdOutFile);
    } catch (FileNotFoundException e) {
      throw new MojoExecutionException("Could not open standard output file for writing", e);
    }

    stdOuts.add(stdOutFileOut);
    if (consoleLog) {
      stdOuts.add(new SimpleWriterOutputStream(System.out));
    }
  }

  @Override
  protected void doExecute() throws MojoExecutionException, MojoFailureException {

    if (distOnly) {
      super.doExecute();
      return;
    }

    if (skipTests) {
      return;
    }

    super.doExecute();

    getLog().info("OSGi Integrations tests running started");

    File reportFolderFile = initializeReportFolder();

    TestResult testResultSum = new TestResult();
    List<TestResult> testResults = new ArrayList<>();

    for (DistributedEnvironmentData distributedEnvironmentData : distributedEnvironmentDataCollection) { // CS_DISABLE_LINE_LENGTH

      EnvironmentConfiguration environment = distributedEnvironmentData.getEnvironment();

      String environmentId = environment.getId();
      File distFolderFile = distributedEnvironmentData.getDistributionFolder();
      int shutdownTimeout = environment.getShutdownTimeout();
      int timeout = environment.getTestRunningTimeout();

      TestResult testResult = runIntegrationTestsOnEnvironment(
          environmentId, distFolderFile, reportFolderFile, shutdownTimeout, timeout);

      testResults.add(testResult);
      testResultSum.addToSum(testResult);
    }

    printTestResultSum(testResultSum);

    throwExceptionsBasedOnTestResultsIfNecesssary(testResultSum);
  }

  private Closeable doStreamRedirections(final Process process, final File resultFolder)
      throws MojoExecutionException {

    File stdOutFile = new File(resultFolder, "system-out.txt");
    File stdErrFile = new File(resultFolder, "system-error.txt");

    List<OutputStream> stdOuts = new ArrayList<>();
    List<OutputStream> stdErrs = new ArrayList<>();

    defineStandardOutputs(stdOutFile, stdOuts);
    defineStandardOutputs(stdErrFile, stdErrs);

    final DaemonStreamRedirector deamonFileWriterStreamPoller =
        new DaemonStreamRedirector(process.getInputStream(), stdOuts.toArray(new OutputStream[0]),
            getLog());
    try {
      deamonFileWriterStreamPoller.start();
    } catch (IOException e) {
      try {
        deamonFileWriterStreamPoller.close();
      } catch (IOException e1) {
        e.addSuppressed(e1);
      }
      throw new MojoExecutionException("Could not start stream redirector for standard output", e);
    }

    final DaemonStreamRedirector deamonStdErrPoller =
        new DaemonStreamRedirector(process.getErrorStream(), stdErrs.toArray(new OutputStream[0]),
            getLog());
    try {
      deamonStdErrPoller.start();
    } catch (IOException e) {
      try {
        deamonFileWriterStreamPoller.close();
      } catch (IOException e1) {
        e.addSuppressed(e1);
      }
      try {
        deamonStdErrPoller.close();
      } catch (IOException e1) {
        e.addSuppressed(e1);
      }
      throw new MojoExecutionException("Could not start stream redirector for standard output", e);
    }

    return new Closeable() {

      @Override
      public void close() throws IOException {
        IOException thrownE = null;
        try {
          deamonFileWriterStreamPoller.close();
        } catch (IOException e) {
          thrownE = e;
        }
        try {
          deamonStdErrPoller.close();
        } catch (IOException e) {
          if (thrownE != null) {
            thrownE.addSuppressed(e);
          } else {
            thrownE = e;
          }
        }
        if (thrownE != null) {
          throw thrownE;
        }
      }
    };
  }

  private File initializeReportFolder() {
    File reportFolderFile = new File(integrationTestTargetFolder);
    getLog().info("Integration test output directory: " + reportFolderFile.getAbsolutePath());

    if (reportFolderFile.exists()) {
      PluginUtil.deleteFolderRecurse(reportFolderFile);
    }
    reportFolderFile.mkdirs();
    return reportFolderFile;
  }

  private void printEnvironmentProcessStartToLog(final String environemntId) {
    StringBuilder sb = new StringBuilder("\n");
    sb.append("-------------------------------------------------------\n");
    sb.append("Starting test environment: ").append(environemntId).append("\n");
    sb.append("-------------------------------------------------------\n\n");
    getLog().info(sb.toString());
  }

  private void printTestResultsOfEnvironment(final String environmentId,
      final TestResult testResult) {
    StringBuilder sb = new StringBuilder("\n");
    sb.append("-------------------------------------------------------\n");
    sb.append("Test environment finished: ").append(environmentId).append("\n");
    sb.append("-------------------------------------------------------\n\n");
    sb.append("Results:\n\n");
    sb.append("Tests run: ").append(testResult.tests);
    sb.append(", Failures: ").append(testResult.failure);
    sb.append(", Errors: ").append(testResult.error);
    sb.append(", Skipped: ").append(testResult.skipped);
    getLog().info(sb.toString());
  }

  private void printTestResultSum(final TestResult testResultSum) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    sb.append("-------------------------------------------------------\n");
    sb.append("I N T E G R A T I O N   T E S T S   ( O S G i)\n");
    sb.append("-------------------------------------------------------\n\n");
    sb.append("Results:\n\n");
    sb.append("Tests run: ").append(testResultSum.tests);
    sb.append(", Failures: ").append(testResultSum.failure);
    sb.append(", Errors: ").append(testResultSum.error);
    sb.append(", Skipped: ").append(testResultSum.skipped);
    sb.append("\n");
    getLog().info(sb.toString());
  }

  private void processResults(final File testResultFolder, final TestResult results)
      throws MojoFailureException {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder documentBuilder = null;
    try {
      documentBuilder = documentBuilderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new MojoFailureException("Failed to process test results", e);
    }

    if (testResultFolder.exists() && testResultFolder.isDirectory()) {
      File[] files = testResultFolder.listFiles();
      for (File resultFile : files) {
        if (resultFile.getName().endsWith(".xml")) {
          processResultXML(results, documentBuilder, resultFile);
        }
      }
    }
  }

  private void processResultXML(final TestResult results, final DocumentBuilder documentBuilder,
      final File resultFile) throws MojoFailureException {
    try {
      Document document = documentBuilder.parse(resultFile);
      Element testSuite = document.getDocumentElement();
      if (!"testsuite".equals(testSuite.getNodeName())) {
        throw new MojoFailureException("Invalid test result xml file "
            + resultFile.getAbsolutePath() + ". Root element is not testsuite.");
      }

      results.tests +=
          IntegrationTestMojo.convertTestSuiteAttributeToInt(testSuite, "tests", resultFile);
      results.failure +=
          IntegrationTestMojo.convertTestSuiteAttributeToInt(testSuite, "failures", resultFile);
      results.error +=
          IntegrationTestMojo.convertTestSuiteAttributeToInt(testSuite, "errors", resultFile);
      results.skipped +=
          IntegrationTestMojo.convertTestSuiteAttributeToInt(testSuite, "skipped", resultFile);
    } catch (SAXException e) {
      throw new MojoFailureException(
          "Invalid test result file " + resultFile.getAbsolutePath());
    } catch (IOException e) {
      throw new MojoFailureException("Error during processing result file "
          + resultFile.getAbsolutePath());
    }
  }

  private String[] resolveCommandForEnvironment(final File distFolderFile,
      final String uniqueLaunchId)
      throws MojoFailureException {

    EnvironmentType distributedEnvironment =
        distEnvConfigProvider.getOverriddenDistributedEnvironmentConfig(
            new File(distFolderFile, DistConstants.FILE_NAME_EOSGI_DIST_CONFIG),
            UseByType.INTEGRATION_TEST);

    LaunchConfigurationDTO environmentConfigurationDTO =
        distEnvConfigProvider.getLaunchConfiguration(distributedEnvironment);

    List<String> command = new ArrayList<>();

    command.add(PluginUtil.getJavaCommand());

    String classPath = environmentConfigurationDTO.classpath;
    if ((classPath != null) && !classPath.trim().isEmpty()) {
      command.add("-classpath");
      command.add(classPath);
    }

    command.addAll(environmentConfigurationDTO.vmArguments);

    command.add("-D" + DistConstants.SYSPROP_LAUNCH_UNIQUE_ID + "=" + uniqueLaunchId);

    command.add(environmentConfigurationDTO.mainClass);

    command.addAll(environmentConfigurationDTO.programArguments);

    return command.toArray(new String[] {});
  }

  private TestResult runIntegrationTestsOnEnvironment(final String environmentId,
      final File distFolderFile, final File reportFolderFile, final int shutdownTimeout,
      final int testRunTimeout)
      throws MojoFailureException, MojoExecutionException {

    printEnvironmentProcessStartToLog(environmentId);

    TestResult testResult = new TestResult();

    String uniqueLaunchId = UUID.randomUUID().toString();
    String[] command = resolveCommandForEnvironment(distFolderFile, uniqueLaunchId);

    try {

      File testResultFolder =
          PluginUtil.subFolderFile(reportFolderFile, environmentId, "test-result");
      testResultFolder.mkdirs();

      ProcessBuilder processBuilder = createTestProcessBuilder(
          environmentId, distFolderFile, command, testResultFolder);

      Process process = processBuilder.start();

      boolean timeoutHappened = false;

      File outputFolderFile =
          PluginUtil.subFolderFile(reportFolderFile, environmentId, "console-output");
      outputFolderFile.mkdirs();

      ShutdownHook shutdownHook = new ShutdownHook(process, uniqueLaunchId, shutdownTimeout);
      Runtime runtime = Runtime.getRuntime();
      runtime.addShutdownHook(shutdownHook);

      try (Closeable redirectionCloseable = doStreamRedirections(process,
          outputFolderFile)) {
        waitForProcess(process, testRunTimeout);

        if (process.isAlive()) {
          getLog().warn("Test running process did not stop until timeout. Forcing to stop it...");
          timeoutHappened = true;
          shutdownProcess(process, uniqueLaunchId, shutdownTimeout, -1);
        }
      } finally {
        runtime.removeShutdownHook(shutdownHook);
      }

      if (timeoutHappened) {
        throw new MojoExecutionException("Test process of environment "
            + "[" + environmentId + "] did not finish within timeout");
      }

      checkExitError(testResultFolder, environmentId);
      checkExitCode(process, environmentId);

      getLog().info("Analyzing test results...");

      processResults(testResultFolder, testResult);

      printTestResultsOfEnvironment(environmentId, testResult);
    } catch (IOException e) {
      throw new MojoExecutionException("Error during running integration tests", e);
    }

    return testResult;
  }

  private void shutdownProcess(final Process process, final String uniqueLaunchId,
      final int shutdownTimeout, final int code) {

    getLog().warn("Stopping test process: " + process);
    if (!process.isAlive()) {
      return;
    }

    try (EOSGiVMManager vmManager = createEOSGiVMManager()) {
      String virtualMachineId = vmManager.getVirtualMachineIdByIUniqueLaunchId(uniqueLaunchId);
      if (virtualMachineId != null) {
        vmManager.shutDownVirtualMachine(virtualMachineId, code, null);
      }
    } catch (Exception e) {
      getLog().error("Could not stop VM via Attach. Shutting it down forcibly", e);
      process.destroyForcibly();
      return;
    }

    try {
      process.waitFor(shutdownTimeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      // Do nothing
    }

    if (process.isAlive()) {
      process.destroyForcibly();
    }

  }

  private void throwExceptionsBasedOnTestResultsIfNecesssary(final TestResult resultSum)
      throws MojoFailureException {
    if ((resultSum.error > 0) || (resultSum.failure > 0)) {
      throw new MojoFailureException("Error during running OSGi integration tests");
    }
  }

  private void waitForProcess(final Process process, final long timeout) {

    long startTime = System.currentTimeMillis();
    long nextExpectedLogging = startTime + LOGGING_INTERVAL;
    long latestEndTime = startTime + timeout;
    long currentTime = startTime;

    while (process.isAlive() && (currentTime < latestEndTime)) {
      try {
        Thread.sleep(TIMEOUT_CHECK_INTERVAL);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        getLog().info("Waiting for tests was interrupted.");
        return;
      }
      if (!consoleLog && (currentTime > nextExpectedLogging)) {
        long secondsSinceStart = (nextExpectedLogging - startTime) / MILLISECOND_NUM_IN_SECOND;
        getLog().info("Waiting for test results since " + secondsSinceStart + "s");
        nextExpectedLogging = nextExpectedLogging + LOGGING_INTERVAL;
      }
      currentTime = System.currentTimeMillis();
    }
  }
}
