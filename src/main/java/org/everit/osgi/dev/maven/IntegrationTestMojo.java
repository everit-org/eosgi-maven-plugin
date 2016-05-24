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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.everit.osgi.dev.dist.util.configuration.LaunchConfigurationDTO;
import org.everit.osgi.dev.dist.util.configuration.schema.EnvironmentType;
import org.everit.osgi.dev.dist.util.configuration.schema.UseByType;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.dto.DistributedEnvironmenData;
import org.everit.osgi.dev.maven.util.DaemonStreamRedirector;
import org.everit.osgi.dev.maven.util.PluginUtil;
import org.everit.osgi.dev.testrunner.TestRunnerConstants;
import org.rzo.yajsw.os.OperatingSystem;
import org.rzo.yajsw.os.Process;
import org.rzo.yajsw.os.ProcessManager;
import org.rzo.yajsw.os.ms.win.w32.WindowsXPProcess;
import org.rzo.yajsw.os.posix.bsd.BSDProcess;
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

    ShutdownHook(final Process process, final int shutdownTimeout) {
      this.process = process;
      this.shutdownTimeout = shutdownTimeout;
    }

    @Override
    public void run() {
      shutdownProcess(process, shutdownTimeout, 0);
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
  @Parameter(property = "eosgi.distOnly", defaultValue = "false")
  protected boolean distOnly = false;

  /**
   * Skipping this plugin.
   */
  @Parameter(property = "eosgi.test.skip", defaultValue = "false")
  protected boolean skipTests = false;

  private void checkExitCode(final Process process, final String environmentId)
      throws MojoExecutionException {
    int exitCode = process.getExitCode();
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

  private File createTempFolder(final String environmentId) throws IOException {
    File tmpPath = File.createTempFile("eosgi-" + environmentId, "-tmp");
    tmpPath.delete();
    tmpPath.mkdir();
    return tmpPath;
  }

  private Process createTestProcess(final String environmentId, final File workingDirFile,
      final String[] command, final File resultFolder, final File tmpPathFile) {
    String title = "EOSGi TestProcess - " + environmentId;

    OperatingSystem operatingSystem = OperatingSystem.instance();
    getLog().info("[" + title + "] Operating system is "
        + operatingSystem.getOperatingSystemName());

    String lowerCaseOperatingSystemName =
        operatingSystem.getOperatingSystemName().toLowerCase(Locale.getDefault());

    Process process;
    if (lowerCaseOperatingSystemName.contains("linux")
        || lowerCaseOperatingSystemName.startsWith("mac os x")) {
      getLog().info("[" + title + "] Starting BSD process");
      process = new BSDProcess();
    } else {
      ProcessManager processManager = operatingSystem.processManagerInstance();
      process = processManager.createProcess();
    }
    process.setTitle(title);

    process.setCommand(command);
    getLog().info("[" + title + "] Command: " + Arrays.deepToString(command));

    String tmpPath = tmpPathFile.getAbsolutePath();
    process.setTmpPath(tmpPath);
    getLog().info("[" + title + "] Tmp path: " + tmpPath);

    process.setVisible(false);
    process.setTeeName(null);
    process.setPipeStreams(true, false);
    process.setLogger(Logger.getLogger("eosgi"));

    String workingDir = workingDirFile.getAbsolutePath();
    process.setWorkingDir(workingDir);
    getLog().info("[" + title + "] Working dir: " + workingDir);

    Map<String, String> envMap = new HashMap<String, String>(System.getenv());
    envMap.put(TestRunnerConstants.ENV_STOP_AFTER_TESTS, Boolean.TRUE.toString());
    envMap.put(TestRunnerConstants.ENV_TEST_RESULT_FOLDER, resultFolder.getAbsolutePath());

    List<String[]> env = PluginUtil.convertMapToList(envMap);
    process.setEnvironment(env);
    getLog().info("[" + title + "] Environment: "
        + Arrays.deepToString(env.toArray(new String[][] {})));

    return process;
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

    File testReportFolderFile = initializeReportFolder();

    TestResult testResultSum = new TestResult();
    List<TestResult> testResults = new ArrayList<>();

    for (DistributedEnvironmenData distributedEnvironmentData : distributedEnvironmentDataCollection) { // CS_DISABLE_LINE_LENGTH

      EnvironmentConfiguration environment = distributedEnvironmentData.getEnvironment();

      String environmentId = environment.getId();
      File distFolderFile = distributedEnvironmentData.getDistributionFolder();
      int shutdownTimeout = environment.getShutdownTimeout();
      int timeout = environment.getTimeout();

      TestResult testResult = runIntegrationTestsOnEnvironment(
          environmentId, distFolderFile, testReportFolderFile, shutdownTimeout, timeout);

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

    List<OutputStream> stdOuts = new ArrayList<OutputStream>();
    List<OutputStream> stdErrs = new ArrayList<OutputStream>();

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
    File testReportFolderFile = new File(reportFolder);
    getLog().info("Integration test output directory: " + testReportFolderFile.getAbsolutePath());

    if (testReportFolderFile.exists()) {
      PluginUtil.deleteFolderRecurse(testReportFolderFile);
    }
    testReportFolderFile.mkdirs();
    return testReportFolderFile;
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

  private void processResults(final File resultFolder, final TestResult results)
      throws MojoFailureException {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder documentBuilder = null;
    try {
      documentBuilder = documentBuilderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new MojoFailureException("Failed to process test results", e);
    }

    if (resultFolder.exists() && resultFolder.isDirectory()) {
      File[] files = resultFolder.listFiles();
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

  private String[] resolveCommandForEnvironment(final File distFolderFile)
      throws MojoFailureException {

    EnvironmentType distributedEnvironment =
        distEnvConfigProvider.getOverriddenDistributedEnvironmentConfig(distFolderFile,
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

    command.add(environmentConfigurationDTO.mainClass);

    command.addAll(environmentConfigurationDTO.programArguments);

    return command.toArray(new String[] {});
  }

  private TestResult runIntegrationTestsOnEnvironment(final String environmentId,
      final File distFolderFile, final File testReportFolderFile, final int shutdownTimeout,
      final int timeout)
      throws MojoFailureException, MojoExecutionException {

    printEnvironmentProcessStartToLog(environmentId);

    TestResult testResult = new TestResult();

    String[] command = resolveCommandForEnvironment(distFolderFile);

    try {

      File resultFolder = new File(testReportFolderFile, environmentId);
      resultFolder.mkdirs();
      File tmpPath = createTempFolder(environmentId);

      Process process = createTestProcess(
          environmentId, distFolderFile, command, resultFolder, tmpPath);

      boolean timeoutHappened = false;

      ShutdownHook shutdownHook = new ShutdownHook(process, shutdownTimeout);
      Runtime.getRuntime().addShutdownHook(shutdownHook);

      boolean started = process.start();
      if (!started) {
        throw new MojoFailureException(
            "Could not start environment with command " + process.getCommand()
                + " in working dir " + process.getWorkingDir());
      }

      AutoCloseable redirectionCloseable = doStreamRedirections(process, resultFolder);
      try {
        waitForProcess(process, timeout);

        if (process.isRunning()) {
          getLog().warn("Test running process did not stop until timeout. Forcing to stop it...");
          timeoutHappened = true;
          shutdownProcess(process, shutdownTimeout, -1);
        }
      } finally {
        try {
          redirectionCloseable.close();
        } catch (Exception e) {
          throw new MojoExecutionException("Could not close stream redirectors", e);
        }
      }

      PluginUtil.deleteFolderRecurse(tmpPath);

      if (timeoutHappened) {
        throw new MojoExecutionException("Test process of environment "
            + "[" + environmentId + "] did not finish within timeout");
      }

      checkExitError(resultFolder, environmentId);
      checkExitCode(process, environmentId);

      getLog().info("Analyzing test results...");

      processResults(resultFolder, testResult);

      printTestResultsOfEnvironment(environmentId, testResult);
    } catch (IOException e) {
      throw new MojoExecutionException("Error during running integration tests", e);
    }

    return testResult;
  }

  private void shutdownProcess(final Process process, final int shutdownTimeout, final int code) {

    getLog().warn("Stopping test process: " + process.getPid());

    if (!process.isRunning()) {
      return;
    }

    if (process instanceof WindowsXPProcess) {
      // In case of windows xp process we must kill the process with a command as there is no
      // visible window and kill tree command of YAJSW does not work. Hopefully this is a temporary
      // solution.
      OperatingSystem operatingSystem = OperatingSystem.instance();
      ProcessManager processManagerInstance = operatingSystem.processManagerInstance();
      Process killProcess = processManagerInstance.createProcess();
      String killCommand = "taskkill /F /T /PID " + process.getPid();
      getLog().warn("Killing windows process with command: " + killCommand + "");
      killProcess.setCommand(killCommand);
      killProcess.setVisible(false);
      killProcess.start();
      process.waitFor(shutdownTimeout);
    } else {
      process.stop(shutdownTimeout, code);
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

    while (process.isRunning() && (currentTime < latestEndTime)) {
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
