/**
 * This file is part of Everit - Maven OSGi plugin.
 *
 * Everit - Maven OSGi plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Maven OSGi plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Maven OSGi plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.maven;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.everit.osgi.dev.maven.jaxb.dist.definition.CommandType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.LauncherType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.LaunchersType;
import org.everit.osgi.dev.maven.util.DistUtil;
import org.everit.osgi.dev.maven.util.EOsgiConstants;
import org.everit.osgi.dev.testrunner.TestRunnerConstants;
import org.rzo.yajsw.os.OperatingSystem;
import org.rzo.yajsw.os.Process;
import org.rzo.yajsw.os.ProcessManager;
import org.rzo.yajsw.os.posix.bsd.BSDProcess;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@Mojo(name = "integration-test", defaultPhase = LifecyclePhase.INTEGRATION_TEST, requiresProject = true,
        requiresDependencyResolution = ResolutionScope.TEST)
public class IntegrationTestMojo extends DistMojo {

    private class ShutdownHook extends Thread {

        private final Process process;

        private final int shutdownTimeout;

        public ShutdownHook(final Process process, final int shutdownTimeout) {
            this.process = process;
            this.shutdownTimeout = shutdownTimeout;
        }

        @Override
        public void run() {
            if (process.isRunning()) {
                getLog().warn("Stopping process due to shutdown hook: " + process.getPid());
                process.stop(shutdownTimeout, -1);
            }
        }
    }

    private static class TestResult {
        public String environmentId;
        public int error;
        public int expectedTestNum;
        public int failure;
        public int skipped;
        public int tests;
    }

    /**
     * Constant of the MANIFEST header key to count the {@link #expectedNumberOfIntegrationTests}.
     */
    private static final String EXPECTED_NUMBER_OF_INTEGRATION_TESTS = "EOSGi-TestNum";

    /**
     * If link than the generated files in the dist folder will be links instead of real copied files. Two possible
     * values: symbolicLink, file.
     */
    @Parameter(property = "eosgi.copyMode", defaultValue = EOsgiConstants.COPYMODE_SYMBOLIC_LINK)
    protected String copyMode;

    @Parameter(defaultValue = "${executedProject}")
    protected MavenProject executedProject;

    /**
     * The jacoco code coverage generation settings. To see the possible settings see {@link JacocoSettings}.
     */
    @Parameter
    protected JacocoSettings jacoco;
    /**
     * Skipping this plugin.
     */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    protected boolean skipTests = false;

    /**
     * The folder where the integration test reports will be placed. Please note that the content of this folder will be
     * deleted before running the tests.
     */
    @Parameter(property = "eosgi.testReportFolder", defaultValue = "${project.build.directory}/eosgi-itests-reports")
    protected String testReportFolder;

    private int calculateExpectedTestNum(final DistributedEnvironment distributedEnvironment) {
        int result = 0;
        for (DistributableArtifact distributableArtifact : distributedEnvironment.getDistributableArtifacts()) {
            Attributes mainAttributes = distributableArtifact.getManifest().getMainAttributes();
            String currentExpectedNumberString = mainAttributes.getValue(EXPECTED_NUMBER_OF_INTEGRATION_TESTS);
            if ((currentExpectedNumberString != null) && !currentExpectedNumberString.isEmpty()) {
                long currentExpectedNumber = Long.valueOf(currentExpectedNumberString).longValue();
                result += currentExpectedNumber;
            }
        }
        return result;
    }

    private LauncherType calculateLauncherForCurrentOS(final DistributedEnvironment distributedEnvironment) {

        LaunchersType launchers = distributedEnvironment.getDistributionPackage().getLaunchers();
        if (launchers == null) {
            return null;
        }

        List<LauncherType> launcherList = launchers.getLauncher();

        if (launcherList.size() == 0) {
            return null;
        }

        String os = DistUtil.getOS();

        LauncherType selectedLauncher = null;
        Iterator<LauncherType> iterator = launcherList.iterator();
        while ((selectedLauncher == null) && iterator.hasNext()) {
            LauncherType launcher = iterator.next();
            if (os.equals(launcher.getOs())) {
                selectedLauncher = launcher;
            }
        }
        return selectedLauncher;
    }

    private boolean checkExitError(final File resultFolder, final String environmentId) {
        File exitErrorFile = new File(resultFolder, TestRunnerConstants.SYSTEM_EXIT_ERROR_FILE_NAME);
        if (exitErrorFile.exists()) {
            StringBuilder sb = new StringBuilder();

            try (FileInputStream fin = new FileInputStream(exitErrorFile)) {
                InputStreamReader reader = new InputStreamReader(fin);
                BufferedReader br = new BufferedReader(reader);
                String line = br.readLine();
                while (line != null) {
                    sb.append(line).append("\n");
                    line = br.readLine();
                }
            } catch (FileNotFoundException e) {
                getLog().error("Could not find file " + exitErrorFile.getAbsolutePath(), e);
            } catch (IOException e) {
                getLog().error("Error during reading exit error file " + exitErrorFile.getAbsolutePath(), e);
            }
            getLog().error(
                    "Error during stopping the JVM of the environment " + environmentId
                            + ". Information can be found at " + exitErrorFile.getAbsolutePath()
                            + ". Content of the file is: \n" + sb.toString());

            return true;
        }
        return false;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests) {
            return;
        }

        processJacocoSettings();
        super.execute();

        File testReportFolderFile = new File(testReportFolder);

        getLog().info("OSGi Integrations tests running started");
        getLog().info("Integration test output directory: " + testReportFolderFile.getAbsolutePath());

        if (testReportFolderFile.exists()) {
            DistUtil.deleteFolderRecurse(testReportFolderFile);
        }
        testReportFolderFile.mkdirs();

        List<TestResult> testResults = new ArrayList<TestResult>();
        for (DistributedEnvironment distributedEnvironment : distributedEnvironments) {
            TestResult testResult = new TestResult();
            testResult.environmentId = distributedEnvironment.getEnvironment().getId();
            testResult.expectedTestNum = calculateExpectedTestNum(distributedEnvironment);
            testResults.add(testResult);
            LauncherType launcher = calculateLauncherForCurrentOS(distributedEnvironment);

            if (launcher == null) {
                throw new MojoFailureException("No start command specified for tests in the distribution package of "
                        + distributedEnvironment.getEnvironment().getId());
            }

            CommandType startCommand = launcher.getStartCommand();
            String folder = startCommand.getFolder();
            File commandFolder = distributedEnvironment.getDistributionFolder();

            if (folder != null) {
                commandFolder = new File(commandFolder, folder);
            }

            try {

                File resultFolder = new File(testReportFolderFile, distributedEnvironment.getEnvironment().getId());
                resultFolder.mkdirs();

                File stdOutFile = new File(resultFolder, "system-out.txt");
                File stdErrFile = new File(resultFolder, "system-error.txt");

                OperatingSystem operatingSystem = OperatingSystem.instance();
                Process process;
                getLog().info("Operating system is " + operatingSystem.getOperatingSystemName());
                if (operatingSystem.getOperatingSystemName().toLowerCase().contains("linux")) {
                    getLog().info("Starting BSD process");
                    process = new BSDProcess();
                } else {
                    ProcessManager processManager = operatingSystem.processManagerInstance();
                    process = processManager.createProcess();
                }
                process.setTitle("EOSGi TestProcess - " + distributedEnvironment.getEnvironment().getId());

                process.setCommand(startCommand.getValue().split(" "));
                File tmpPath = File.createTempFile("eosgi-", "-tmp");
                tmpPath.delete();
                tmpPath.mkdir();
                getLog().info("Setting tmp path: " + tmpPath.getAbsolutePath());
                process.setTmpPath(tmpPath.getAbsolutePath());
                process.setTeeName(null);
                process.setPipeStreams(true, false);
                process.setLogger(Logger.getLogger("eosgi"));
                // process.setPipeStreams(true, true);

                Map<String, String> envMap = new HashMap<String, String>(System.getenv());
                envMap.put(TestRunnerConstants.ENV_STOP_AFTER_TESTS, Boolean.TRUE.toString());
                envMap.put(TestRunnerConstants.ENV_TEST_RESULT_FOLDER, resultFolder.getAbsolutePath());

                List<String[]> env = DistUtil.convertMapToList(envMap);

                process.setEnvironment(env);
                process.setWorkingDir(commandFolder.getAbsolutePath());

                boolean timeoutHappened = false;
                ShutdownHook shutdownHook = new ShutdownHook(process, distributedEnvironment.getEnvironment()
                        .getShutdownTimeout());
                Runtime.getRuntime().addShutdownHook(shutdownHook);

                boolean started = process.start();
                if (!started) {
                    throw new MojoFailureException("Could not start environment with command " + process.getCommand()
                            + " in working dir " + process.getWorkingDir());
                }

                InputStream processOutput = process.getInputStream();
                DaemonFileWriterStreamPoller deamonFileWriterStreamPoller =
                        new DaemonFileWriterStreamPoller(processOutput, stdOutFile);
                deamonFileWriterStreamPoller.start();

                DaemonFileWriterStreamPoller deamonStdErrPoller =
                        new DaemonFileWriterStreamPoller(process.getErrorStream(), stdErrFile);
                deamonStdErrPoller.start();

                waitForProcessWithTimeoutAndLogging(process, distributedEnvironment.getEnvironment());

                if (process.isRunning()) {
                    getLog().warn("Test running process did not stop until timeout. Forcing to stop it...");
                    timeoutHappened = true;
                    process.stop(distributedEnvironment.getEnvironment().getShutdownTimeout(), -1);
                }

                deamonFileWriterStreamPoller.close();
                deamonStdErrPoller.close();
                DistUtil.deleteFolderRecurse(tmpPath);

                String environmentId = distributedEnvironment.getEnvironment().getId();

                if (timeoutHappened) {
                    throw new MojoExecutionException("Test process of environment " + environmentId
                            + " did not finish within timeout");
                }

                boolean exitError = checkExitError(resultFolder, environmentId);
                int exitCode = process.getExitCode();
                if (exitCode != 0) {
                    throw new MojoExecutionException("Test Process of environment " + environmentId
                            + " finished with exit code " + exitCode);
                }

                getLog().info("Analyzing test results...");

                if (exitError) {
                    throw new MojoFailureException("Could not shut down the JVM of the environment " + environmentId
                            + " in a nice way");
                }

                processResults(resultFolder, testResult);
            } catch (IOException e) {
                throw new MojoExecutionException("Error during running integration tests", e);
            }
        }

        List<TestResult> expecationErroredResults = new ArrayList<TestResult>();
        TestResult resultSum = new TestResult();

        for (TestResult testResult : testResults) {
            resultSum.tests += testResult.tests;
            resultSum.error += testResult.error;
            resultSum.failure += testResult.failure;
            resultSum.skipped += testResult.skipped;
            resultSum.expectedTestNum += testResult.expectedTestNum;
            if (testResult.expectedTestNum != testResult.tests) {
                expecationErroredResults.add(testResult);
            }
        }

        StringBuilder testLogTextSB = new StringBuilder("\n-------------------------------------------------------\n");
        testLogTextSB.append("I N T E G R A T I O N   T E S T S   ( O S G I)\n")
                .append("-------------------------------------------------------\n\n").append("Results:\n\n")
                .append("Tests run: ").append(resultSum.tests).append(", Failures: ").append(resultSum.failure)
                .append(", Errors: ").append(resultSum.error).append(", Skipped: ").append(resultSum.skipped)
                .append("\n");
        getLog().info(testLogTextSB.toString());

        if ((resultSum.error > 0) || (resultSum.failure > 0)) {
            throw new MojoFailureException("Error during running OSGi integration tests");
        }

        if (expecationErroredResults.size() > 0) {
            for (TestResult testResult : expecationErroredResults) {
                getLog().error(
                        "Error at test environment '" + testResult.environmentId + "'. Expected test number is "
                                + testResult.expectedTestNum + " while " + testResult.tests + " number of tests ran.");
            }
            throw new MojoFailureException("Number of expected tests " + resultSum.expectedTestNum + " while "
                    + resultSum.tests + " tests ran.");
        }
    }

    @Override
    public String getCopyMode() {
        return copyMode;
    }

    public JacocoSettings getJacoco() {
        return jacoco;
    }

    private void processJacocoSettings() {
        if (jacoco != null) {
            File globalReportFolderFile = new File(testReportFolder);

            System.out.println(pluginArtifactMap.keySet());
            Artifact jacocoAgentArtifact = pluginArtifactMap.get("org.jacoco:org.jacoco.agent");
            File jacocoAgentFile = jacocoAgentArtifact.getFile();
            String jacocoAgentAbsPath = jacocoAgentFile.getAbsolutePath();

            StringBuilder sb = new StringBuilder("-javaagent:");
            sb.append(jacocoAgentAbsPath.replace("\\", "\\\\"));
            sb.append("=append=").append(Boolean.valueOf(jacoco.isAppend()).toString());
            sb.append("\\,dumponexit=").append(Boolean.valueOf(jacoco.isDumponexit()).toString());
            if (jacoco.getIncludes() != null) {
                sb.append("\\,includes=").append(jacoco.getIncludes());
            }
            if (jacoco.getExcludes() != null) {
                sb.append("\\,excludes=").append(jacoco.getExcludes());
            }
            String jacocoAgentParam = sb.toString();
            for (EnvironmentConfiguration environment : getEnvironmentsToProcess()) {
                File reportFolderFile = new File(globalReportFolderFile, environment.getId());
                reportFolderFile.mkdirs();
                File jacocoExecFile = new File(reportFolderFile, "jacoco.exec");
                StringBuilder envSb = new StringBuilder(jacocoAgentParam);
                envSb.append("\\,destfile=").append(jacocoExecFile.getAbsolutePath().replace("\\", "\\\\"));
                envSb.append("\\,sessionid=").append(environment.getId()).append("_").append(new Date().getTime());

                List<String> vmOptions = environment.getVmOptions();
                if (vmOptions == null) {
                    vmOptions = new ArrayList<String>();
                    environment.setVmOptions(vmOptions);
                }
                vmOptions.add(envSb.toString());
            }
        }
    }

    private void processResults(final File resultFolder, final TestResult results) throws MojoFailureException {
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
                    try {
                        Document document = documentBuilder.parse(resultFile);
                        Element testSuite = document.getDocumentElement();
                        if (!"testsuite".equals(testSuite.getNodeName())) {
                            throw new MojoFailureException("Invalid test result xml file "
                                    + resultFile.getAbsolutePath() + ". Root element is not testsuite.");
                        }
                        String tests = testSuite.getAttribute("tests");
                        String errors = testSuite.getAttribute("errors");
                        String failures = testSuite.getAttribute("failures");
                        String skipped = testSuite.getAttribute("skipped");

                        if ((tests == null) || "".equals(tests) || (errors == null) || "".equals(errors)
                                || (failures == null) || "".equals(failures) || (skipped == null) || "".equals(skipped)) {

                            throw new MojoFailureException("Invalid test result file " + resultFile.getAbsolutePath()
                                    + ". One of the attributes in testSuite is not defined.");
                        }

                        try {
                            results.tests += Integer.parseInt(tests);
                            results.failure += Integer.parseInt(failures);
                            results.error += Integer.parseInt(errors);
                            results.skipped += Integer.parseInt(skipped);
                        } catch (NumberFormatException e) {
                            throw new MojoFailureException("Invalid test result file " + resultFile.getAbsolutePath()
                                    + ". The testSuite does not contains invalid attribute.");
                        }
                    } catch (SAXException e) {
                        throw new MojoFailureException("Invalid test result file " + resultFile.getAbsolutePath());
                    } catch (IOException e) {
                        throw new MojoFailureException("Error during processing result file "
                                + resultFile.getAbsolutePath());
                    }
                }
            }
        }
    }

    public void setJacoco(final JacocoSettings jacoco) {
        this.jacoco = jacoco;
    }

    private void waitForProcessWithTimeoutAndLogging(final Process process,
            final EnvironmentConfiguration environment) {
        final long loggingInterval = 5000;
        final long timeout = environment.getTimeout();
        final long startTime = System.currentTimeMillis();

        long nextExpectedLogging = startTime + loggingInterval;

        final long latestEndTime = startTime + timeout;
        long currentTime = startTime;
        while (process.isRunning() && currentTime < latestEndTime) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                getLog().info("Waiting for tests was interrupted.");
                return;
            }
            if (currentTime > nextExpectedLogging) {
                long secondsSinceStart = (nextExpectedLogging - startTime) / 1000;
                getLog().info("Waiting for test results since " + secondsSinceStart + "s");
                nextExpectedLogging = nextExpectedLogging + loggingInterval;
            }
            currentTime = System.currentTimeMillis();
        }
    }
}
