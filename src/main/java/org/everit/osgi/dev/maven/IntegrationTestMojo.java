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
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ArtifactType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ArtifactsType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.BundleDataType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.CommandType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.LauncherType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.LaunchersType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.OSGiActionType;
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
            shutdownProcess(process, shutdownTimeout);
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

    @Parameter(defaultValue = "${executedProject}")
    protected MavenProject executedProject;

    /**
     * The jacoco code coverage generation settings. To see the possible settings see {@link JacocoSettings}.
     */
    @Parameter
    protected JacocoSettings jacoco;

    /**
     * Whether to log the output of the started test JVMs to the standard output and standard error or not.
     */
    @Parameter(property = "eosgi.consoleLog", defaultValue = "true")
    protected boolean consoleLog = true;

    /**
     * Skipping this plugin.
     */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    protected boolean skipTests = false;

    private int calculateExpectedTestNum(final DistributedEnvironment distributedEnvironment) {
        int result = 0;
        ArtifactsType artifacts = distributedEnvironment.getDistributionPackage().getArtifacts();
        if (artifacts == null) {
            return 0;
        }
        List<ArtifactType> artifactList = artifacts.getArtifact();
        Set<String> artifactsKeys = new HashSet<>();
        for (ArtifactType artifactType : artifactList) {
            BundleDataType bundleDataType = artifactType.getBundle();
            if (bundleDataType != null && !OSGiActionType.NONE.equals(bundleDataType.getAction())) {
                String artifactKey = artifactType.getGroupId() + ":" + artifactType.getArtifactId() + ":"
                        + artifactType.getVersion() + ":" + evaluateArtifactType(artifactType.getType()) + ":"
                        + evaluateClassifier(artifactType.getClassifier());

                artifactsKeys.add(artifactKey);
            }
        }

        for (DistributableArtifact distributableArtifact : distributedEnvironment.getDistributableArtifacts()) {
            DistributableArtifactBundleMeta bundle = distributableArtifact.getBundle();
            if (bundle != null) {
                Artifact artifact = distributableArtifact.getArtifact();

                String artifactKey = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                        + artifact.getVersion() + ":" + evaluateArtifactType(artifact.getType()) + ":"
                        + evaluateClassifier(artifact.getClassifier());

                if (artifactsKeys.contains(artifactKey)) {
                    Attributes mainAttributes = distributableArtifact.getManifest().getMainAttributes();

                    String currentExpectedNumberString = mainAttributes.getValue(EXPECTED_NUMBER_OF_INTEGRATION_TESTS);
                    if ((currentExpectedNumberString != null) && !currentExpectedNumberString.isEmpty()) {
                        long currentExpectedNumber = Long.valueOf(currentExpectedNumberString).longValue();
                        result += currentExpectedNumber;
                    }
                }
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

        String os = PluginUtil.getOS();

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

    private String evaluateArtifactType(final String artifactType) {
        if (artifactType == null || artifactType.trim().equals("")) {
            return "jar";
        }
        return artifactType;
    }

    private String evaluateClassifier(final String classifier) {
        if (classifier == null || classifier.trim().length() == 0) {
            return null;
        }
        return classifier;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests) {
            return;
        }

        processJacocoSettings();
        super.execute();

        File testReportFolderFile = new File(reportFolder);

        getLog().info("OSGi Integrations tests running started");
        getLog().info("Integration test output directory: " + testReportFolderFile.getAbsolutePath());

        if (testReportFolderFile.exists()) {
            PluginUtil.deleteFolderRecurse(testReportFolderFile);
        }
        testReportFolderFile.mkdirs();

        List<TestResult> testResults = new ArrayList<TestResult>();
        for (DistributedEnvironment distributedEnvironment : distributedEnvironments) {
            StringBuilder startEnvLogTextSB = new StringBuilder(
                    "\n-------------------------------------------------------\n");
            startEnvLogTextSB.append("Starting test environment: ")
                    .append(distributedEnvironment.getEnvironment().getId())
                    .append("\n")
                    .append("-------------------------------------------------------\n\n");
            getLog().info(startEnvLogTextSB.toString());

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
                process.setVisible(false);
                process.setTeeName(null);
                process.setPipeStreams(true, false);
                process.setLogger(Logger.getLogger("eosgi"));

                Map<String, String> envMap = new HashMap<String, String>(System.getenv());
                envMap.put(TestRunnerConstants.ENV_STOP_AFTER_TESTS, Boolean.TRUE.toString());
                envMap.put(TestRunnerConstants.ENV_TEST_RESULT_FOLDER, resultFolder.getAbsolutePath());

                List<String[]> env = PluginUtil.convertMapToList(envMap);

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

                AutoCloseable redirectionCloseable = doStreamRedirections(process, stdOutFile, stdErrFile);
                try {
                    waitForProcessWithTimeoutAndLogging(process, distributedEnvironment.getEnvironment());

                    if (process.isRunning()) {
                        getLog().warn("Test running process did not stop until timeout. Forcing to stop it...");
                        timeoutHappened = true;
                        shutdownProcess(process, distributedEnvironment.getEnvironment().getShutdownTimeout());
                    }
                } finally {
                    try {
                        redirectionCloseable.close();
                    } catch (Exception e) {
                        throw new MojoExecutionException("Could not close stream redirectors", e);
                    }
                }

                PluginUtil.deleteFolderRecurse(tmpPath);

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

                StringBuilder stopEnvLogTextSB = new StringBuilder(
                        "\n-------------------------------------------------------\n");
                stopEnvLogTextSB.append("Test environment finished: ")
                        .append(distributedEnvironment.getEnvironment().getId())
                        .append("\n")
                        .append("-------------------------------------------------------\n\n").append("Tests run: ")
                        .append(testResult.tests).append(", Failures: ").append(testResult.failure)
                        .append(", Errors: ").append(testResult.error).append(", Skipped: ").append(testResult.skipped)
                        .append("\n");
                getLog().info(stopEnvLogTextSB.toString());
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

    private Closeable doStreamRedirections(Process process, File stdOutFile, File stdErrFile)
            throws MojoExecutionException {
        FileOutputStream stdOutFileOut;
        try {
            stdOutFileOut = new FileOutputStream(stdOutFile);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Could not open standard output file for writing", e);
        }
        List<OutputStream> stdOuts = new ArrayList<OutputStream>();
        stdOuts.add(stdOutFileOut);
        if (consoleLog) {
            stdOuts.add(new OutputStream() {

                @Override
                public void write(int b) throws IOException {
                    System.out.write(b);
                }

                public void close() throws IOException {
                };
            });
        }

        FileOutputStream stdErrFileOut;
        try {
            stdErrFileOut = new FileOutputStream(stdOutFile);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Could not open standard output file for writing", e);
        }
        List<OutputStream> stdErrs = new ArrayList<OutputStream>();
        stdErrs.add(stdErrFileOut);
        if (consoleLog) {
            stdErrs.add(new OutputStream() {

                @Override
                public void write(int b) throws IOException {
                    System.err.write(b);
                }

                public void close() throws IOException {
                };
            });
        }

        final DaemonStreamRedirector deamonFileWriterStreamPoller =
                new DaemonStreamRedirector(process.getInputStream(), stdOuts.toArray(new OutputStream[0]), getLog());
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
                new DaemonStreamRedirector(process.getErrorStream(), stdErrs.toArray(new OutputStream[0]), getLog());
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

    public JacocoSettings getJacoco() {
        return jacoco;
    }

    private void processJacocoSettings() {
        if (jacoco != null) {
            File globalReportFolderFile = new File(reportFolder);

            System.out.println(pluginArtifactMap.keySet());
            Artifact jacocoAgentArtifact = pluginArtifactMap.get("org.jacoco:org.jacoco.agent");
            File jacocoAgentFile = jacocoAgentArtifact.getFile();
            String jacocoAgentAbsPath = jacocoAgentFile.getAbsolutePath();

            StringBuilder sb = new StringBuilder("-javaagent:");
            sb.append(jacocoAgentAbsPath);
            sb.append("=append=").append(Boolean.valueOf(jacoco.isAppend()).toString());
            sb.append(",dumponexit=").append(Boolean.valueOf(jacoco.isDumponexit()).toString());
            if (jacoco.getIncludes() != null) {
                sb.append(",includes=").append(jacoco.getIncludes());
            }
            if (jacoco.getExcludes() != null) {
                sb.append(",excludes=").append(jacoco.getExcludes());
            }
            String jacocoAgentParam = sb.toString();
            for (EnvironmentConfiguration environment : getEnvironmentsToProcess()) {
                File reportFolderFile = new File(globalReportFolderFile, environment.getId());
                reportFolderFile.mkdirs();
                File jacocoExecFile = new File(reportFolderFile, "jacoco.exec");
                StringBuilder envSb = new StringBuilder(jacocoAgentParam);
                envSb.append(",destfile=").append(jacocoExecFile.getAbsolutePath());
                envSb.append(",sessionid=").append(environment.getId()).append("_").append(new Date().getTime());

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

    private void shutdownProcess(Process process, int shutdownTimeout) {
        getLog().warn("Stopping test process: " + process.getPid());
        if (process.isRunning()) {
            if (process instanceof WindowsXPProcess) {
                // In case of windows xp process we must kill the process with a command as there is no visible
                // window and kill tree command of YAJSW does not work. Hopefully this is a temporary solution.
                Log log = getLog();

                OperatingSystem operatingSystem = OperatingSystem.instance();
                ProcessManager processManagerInstance = operatingSystem.processManagerInstance();
                Process killProcess = processManagerInstance.createProcess();
                String killCommand = "taskkill /F /T /PID " + process.getPid();
                log.warn("Killing windows process with command: " + killCommand + "");
                killProcess.setCommand(killCommand);
                killProcess.setVisible(false);
                killProcess.start();
                process.waitFor(shutdownTimeout);
            } else {
                process.stop(shutdownTimeout, -1);
            }
        }
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
            if (!consoleLog && currentTime > nextExpectedLogging) {
                long secondsSinceStart = (nextExpectedLogging - startTime) / 1000;
                getLog().info("Waiting for test results since " + secondsSinceStart + "s");
                nextExpectedLogging = nextExpectedLogging + loggingInterval;
            }
            currentTime = System.currentTimeMillis();
        }
    }
}
