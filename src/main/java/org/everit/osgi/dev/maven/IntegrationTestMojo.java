package org.everit.osgi.dev.maven;

/*
 * Copyright (c) 2011, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.jar.Attributes;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Command;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Launcher;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Launchers;
import org.everit.osgi.dev.maven.util.DistUtil;
import org.everit.osgi.dev.testrunner.TestRunnerActivator;
import org.everit.osgi.dev.testrunner.blocking.BlockingManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * @phase integration-test
 * @goal integration-test
 * @requiresProject true
 * @requiresDependencyResolution test
 * @execute phase="package"
 */
public class IntegrationTestMojo extends DistMojo {

    public static final String ENV_PROCESS_UNIQUE_ID = "EOSGI_PROCESS_ID";

    private static class TestResult {
        public String environmentId;
        public int expectedTestNum;
        public int tests;
        public int skipped;
        public int failure;
        public int error;
    }

    private class ShutdownHook extends Thread {

        private TimeoutChecker timeoutChecker;

        public ShutdownHook(TimeoutChecker timeoutChecker) {
            this.timeoutChecker = timeoutChecker;
        }

        @Override
        public void run() {
            timeoutChecker.timeoutHappen();
            System.out.flush();
        }
    }

    private class TimeoutChecker implements Runnable {
        private final long timeout;

        private final ProcessBuilder killCommand;

        private boolean stopped = false;

        private ShutdownHook shutdownHook;

        public TimeoutChecker(long timeout, ProcessBuilder killCommand) {
            this.timeout = timeout;
            this.killCommand = killCommand;
            this.shutdownHook = new ShutdownHook(this);
            Runtime.getRuntime().addShutdownHook(shutdownHook);

        }

        @Override
        public void run() {
            Log logger = getLog();
            long startTime = new Date().getTime();
            long lastLoggedTime = startTime;
            while (!stopped) {
                long currentTime = new Date().getTime();

                if (currentTime - lastLoggedTime > 5000) {
                    lastLoggedTime = currentTime;
                    long runningSecs = (currentTime - startTime) / 1000;
                    logger.info("Test server is running since " + runningSecs + " seconds. Please wait!");
                }

                if (currentTime - startTime > timeout) {
                    logger.error("Timeout exceeded, forcing to stop server...");
                    logger.info("If you need a higher timeout you can override the default five " +
                            "minutes in the environment configuration");
                    stop();
                    timeoutHappen();
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    System.out.println("WARN: TimeoutChecker thread interrupted.");
                    timeoutHappen();
                }
            }

        }

        public void timeoutHappen() {
            stopped = true;
            StreamRedirector stdoutRedirector = null;
            StreamRedirector stdErrorRedirector = null;
            try {
                Process killProcess = killCommand.start();
                stdoutRedirector = new StreamRedirector(killProcess.getInputStream(), System.out, true, false);
                stdErrorRedirector = new StreamRedirector(killProcess.getErrorStream(), System.out, true, false);

                new Thread(stdErrorRedirector).start();
                new Thread(stdoutRedirector).start();
                try {
                    killProcess.waitFor();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                stdErrorRedirector.stop();
                stdoutRedirector.stop();
            }
        }

        public void stop() {
            stopped = true;
            if (shutdownHook != null) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
        }
    }

    /**
     * Constant of the MANIFEST header key to count the {@link #expectedNumberOfIntegrationTests}.
     */
    private static final String EXPECTED_NUMBER_OF_INTEGRATION_TESTS = "EOSGi-TestNum";

    /**
     * Whether to include the test runner and it's dependencies.
     * 
     * @parameter expression="${eosgi.includeTestRunner}" default-value="true"
     */
    protected boolean includeTestRunner = true;

    /**
     * Whether to include the artifact of the current project or not. If false only the dependencies will be processed.
     * 
     * @parameter expression="${eosgi.includeCurrentProject}" default-value="true"
     */
    protected boolean includeCurrentProject = true;

    /**
     * If link than the generated files in the dist folder will be links instead of real copied files. Two possible
     * values: link, file.
     * 
     * @parameter expression="${eosgi.copyMode}" default-value="link"
     */
    protected String copyMode;

    /**
     * The folder where the integration test reports will be placed. Please note that the content of this folder will be
     * deleted before running the tests.
     * 
     * @parameter expression="${eosgi.testReportFolder}" default-value="${project.build.directory}/eosgi-itests-reports"
     */
    protected String testReportFolder;

    /**
     * Path to folder where the distribution will be generated. The content of this folder will be overridden if the
     * files with same name already exist.
     * 
     * @parameter expression="${eosgi.testDistFolder}" default-value="${project.build.directory}/eosgi-itests-dist"
     */
    protected String distFolder;

    /**
     * @parameter expression="${executedProject}"
     */
    protected MavenProject executedProject;

    /**
     * The jacoco code coverage generation settings. To see the possible settings see {@link JacocoSettings}.
     * 
     * @parameter
     */
    protected JacocoSettings jacoco;

    public String getCopyMode() {
        return copyMode;
    }

    public boolean isIncludeCurrentProject() {
        return includeCurrentProject;
    }

    public boolean isIncludeTestRunner() {
        return includeTestRunner;
    }

    @Override
    public String getDistFolder() {
        return distFolder;
    }
    
    private void processJacocoSettings() {
        if (jacoco != null) {
            File globalReportFolderFile = new File(testReportFolder);
            
            Artifact jacocoAgentArtifact = pluginArtifactMap
                    .get("org.jacoco:org.jacoco.agent");
            File jacocoAgentFile = jacocoAgentArtifact.getFile();
            String jacocoAgentAbsPath = jacocoAgentFile.getAbsolutePath();
            
            StringBuilder sb = new StringBuilder("-javaagent:");
            sb.append(jacocoAgentAbsPath);
            sb.append("=append=").append(Boolean.valueOf(jacoco.isAppend()).toString());
            sb.append("\\,dumponexit=").append(Boolean.valueOf(jacoco.isDumponexit()).toString());
            if (jacoco.getIncludes() != null) {
                sb.append("\\,includes=").append(jacoco.getIncludes());
            }
            if (jacoco.getExcludes() != null) {
                sb.append("\\,excludes=").append(jacoco.getExcludes());
            }
            String jacocoAgentParam = sb.toString();
            for (Environment environment : getEnvironments()) {
                File reportFolderFile = new File(globalReportFolderFile, environment.getId());
                reportFolderFile.mkdirs();
                File jacocoExecFile = new File(reportFolderFile, "jacoco.exec");
                StringBuilder envSb = new StringBuilder(jacocoAgentParam);
                envSb.append("\\,destfile=").append(jacocoExecFile.getAbsolutePath());
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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
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
            Launcher launcher = calculateLauncherForCurrentOS(distributedEnvironment);

            if (launcher == null) {
                throw new MojoFailureException("No start command specified for tests in the distribution package of "
                        + distributedEnvironment.getEnvironment().getId());
            }

            Command startCommand = launcher.getStartCommand();
            String folder = startCommand.getFolder();
            File commandFolder = distributedEnvironment.getDistributionFolder();

            if (folder != null) {
                commandFolder = new File(commandFolder, folder);
            }
            ProcessBuilder pb = new ProcessBuilder(startCommand.getValue().split(" ")).directory(commandFolder);

            try {

                File resultFolder = new File(testReportFolderFile, distributedEnvironment.getEnvironment().getId());
                resultFolder.mkdirs();

                pb.environment().put(TestRunnerActivator.ENV_TEST_RESULT_FOLDER, resultFolder.getAbsolutePath());
                pb.environment().put(BlockingManager.ENV_STOP_AFTER_TESTS, Boolean.TRUE.toString());
                UUID processUUID = UUID.randomUUID();
                pb.environment().put(ENV_PROCESS_UNIQUE_ID, processUUID.toString());

                File stdOutFile = new File(resultFolder, "system-out.txt");
                File stdErrFile = new File(resultFolder, "system-error.txt");

                Command killCommand = launcher.getKillCommand();
                String killFolder = killCommand.getFolder();

                File killCommandFolder = distributedEnvironment.getDistributionFolder();
                if (killFolder != null) {
                    killCommandFolder = new File(killCommandFolder, killFolder);
                }

                ProcessBuilder killPB = new ProcessBuilder(killCommand.getValue().split(" "))
                        .directory(killCommandFolder);
                killPB.environment().put(ENV_PROCESS_UNIQUE_ID, processUUID.toString());
                TimeoutChecker timeoutChecker = new TimeoutChecker(
                        distributedEnvironment.getEnvironment().getTimeout(), killPB);

                new Thread(timeoutChecker).start();

                Process process = pb.start();

                InputStream processOutput = process.getInputStream();
                DeamonFileWriterStreamPoller deamonFileWriterStreamPoller = new DeamonFileWriterStreamPoller(
                        processOutput, stdOutFile);
                deamonFileWriterStreamPoller.start();

                DeamonFileWriterStreamPoller deamonStdErrPoller = new DeamonFileWriterStreamPoller(
                        process.getErrorStream(), stdErrFile);
                deamonStdErrPoller.start();

                int exitValue = 0;
                try {
                    exitValue = process.waitFor();
                } catch (InterruptedException e) {
                    throw new MojoExecutionException("Running test server interrupted", e);
                } finally {
                    deamonFileWriterStreamPoller.close();
                    deamonStdErrPoller.close();
                    timeoutChecker.stop();
                }

                String environmentId = distributedEnvironment.getEnvironment().getId();
                boolean exitError = checkExitError(resultFolder, environmentId);
                if (exitValue != 0) {
                    throw new MojoExecutionException("Test Process finished with exit code " + exitValue);
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

        StringBuilder testLogTextSB = new StringBuilder(
                "\n-------------------------------------------------------\n");
        testLogTextSB
                .append("I N T E G R A T I O N   T E S T S   ( O S G I)\n")
                .append("-------------------------------------------------------\n\n")
                .append("Results:\n\n").append("Tests run: ").append(resultSum.tests).append(", Failures: ")
                .append(resultSum.failure).append(", Errors: ").append(resultSum.error).append(", Skipped: ")
                .append(resultSum.skipped).append("\n");
        getLog().info(testLogTextSB.toString());

        if (resultSum.error > 0 || resultSum.failure > 0) {
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

    private boolean checkExitError(File resultFolder, String environmentId) {
        File exitErrorFile = new File(resultFolder, TestRunnerActivator.SYSTEM_EXIT_ERROR_FILE_NAME);
        if (exitErrorFile.exists()) {
            StringBuilder sb = new StringBuilder();
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(exitErrorFile);
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
            } finally {
                if (fin != null) {
                    try {
                        fin.close();
                    } catch (IOException e) {
                        getLog().error("Could not close file " + exitErrorFile.getAbsolutePath(), e);
                    }
                }
            }
            getLog().error(
                    "Error during stopping the JVM of the environment " + environmentId
                            + ". Information can be found at " + exitErrorFile.getAbsolutePath()
                            + ". Content of the file is: \n" + sb.toString());

            return true;
        }
        return false;
    }

    private void processResults(File resultFolder, TestResult results) throws MojoFailureException {
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

                        if (tests == null || "".equals(tests)
                                || errors == null || "".equals(errors)
                                || failures == null || "".equals(failures)
                                || skipped == null || "".equals(skipped)) {

                            throw new MojoFailureException("Invalid test result file "
                                    + resultFile.getAbsolutePath()
                                    + ". One of the attributes in testSuite is not defined.");
                        }

                        try {
                            results.tests += Integer.parseInt(tests);
                            results.failure += Integer.parseInt(failures);
                            results.error += Integer.parseInt(errors);
                            results.skipped += Integer.parseInt(skipped);
                        } catch (NumberFormatException e) {
                            throw new MojoFailureException("Invalid test result file "
                                    + resultFile.getAbsolutePath()
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

    private Launcher calculateLauncherForCurrentOS(DistributedEnvironment distributedEnvironment) {

        Launchers launchers = distributedEnvironment.getDistributionPackage().getLaunchers();
        if (launchers == null) {
            return null;
        }

        List<Launcher> launcherList = launchers.getLauncher();

        if (launcherList.size() == 0) {
            return null;
        }

        String os = DistUtil.getOS();

        Launcher selectedLauncher = null;
        Iterator<Launcher> iterator = launcherList.iterator();
        while (selectedLauncher == null && iterator.hasNext()) {
            Launcher launcher = iterator.next();
            if (os.equals(launcher.getOs())) {
                selectedLauncher = launcher;
            }
        }
        return selectedLauncher;
    }

    private int calculateExpectedTestNum(DistributedEnvironment distributedEnvironment) {
        int result = 0;
        for (BundleArtifact bundleArtifact : distributedEnvironment.getBundleArtifacts()) {
            Attributes mainAttributes = bundleArtifact.getManifest().getMainAttributes();
            String currentExpectedNumberString = mainAttributes.getValue(EXPECTED_NUMBER_OF_INTEGRATION_TESTS);
            if ((currentExpectedNumberString != null) && !currentExpectedNumberString.isEmpty()) {
                long currentExpectedNumber = Long.valueOf(currentExpectedNumberString).longValue();
                result += currentExpectedNumber;
            }
        }
        return result;
    }

    public JacocoSettings getJacoco() {
        return jacoco;
    }

    public void setJacoco(JacocoSettings jacoco) {
        this.jacoco = jacoco;
    }
}
