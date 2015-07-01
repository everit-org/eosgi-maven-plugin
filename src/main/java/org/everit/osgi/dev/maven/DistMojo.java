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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.tools.generic.EscapeTool;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ArtifactType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ArtifactsType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.BundleDataType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.CopyModeType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.DistributionPackageType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.OSGiActionType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ObjectFactory;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ParseableType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ParseablesType;
import org.everit.osgi.dev.maven.util.ArtifactKey;
import org.everit.osgi.dev.maven.util.DistUtil;
import org.everit.osgi.dev.maven.util.FileManager;
import org.everit.osgi.dev.maven.util.PluginUtil;
import org.everit.osgi.dev.richconsole.RichConsoleConstants;
import org.osgi.framework.Constants;

/**
 * Creates a distribution package for the project. Distribution packages may be provided as Environment parameters or
 * 'equinox', the default option, -may also be used. The structure of the distribution package may be different for
 * different types.
 */
@Mojo(name = "dist", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
@Execute(phase = LifecyclePhase.PACKAGE)
public class DistMojo extends AbstractMojo {

    @Component
    protected ArtifactFactory artifactFactory;

    @Component
    protected ArtifactRepositoryFactory artifactRepositoryFactory;

    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * If link than the generated files in the dist folder will be links instead of real copied files. Two possible
     * values: symbolicLink, file. In case this is an incrementel update, the default mode is the same as the mode of
     * the previous build. In case this is a clean build, the default mode is 'file'.
     */
    @Parameter(property = "eosgi.copyMode")
    protected String copyMode;

    protected final JAXBContext distConfigJAXBContext;

    /**
     * Path to folder where the distribution will be generated. The content of this folder will be overridden if the
     * files with same name already exist.
     *
     */
    @Parameter(property = "eosgi.distFolder", defaultValue = "${project.build.directory}/eosgi-dist")
    protected String distFolder;

    protected List<DistributedEnvironment> distributedEnvironments;

    /**
     * Comma separated list of the id of the environments that should be processed. Default is * that means all
     * environments.
     */
    @Parameter(property = "eosgi.environmentId", defaultValue = "*")
    protected String environmentId = "*";

    /**
     * The environments on which the tests should run.
     */
    @Parameter
    protected EnvironmentConfiguration[] environments;

    private EnvironmentConfiguration[] environmentsToProcess;

    @Parameter(property = "executedProject")
    protected MavenProject executedProject;

    private FileManager fileManager = null;

    /**
     * The jacoco code coverage generation settings. To see the possible settings see {@link JacocoSettings}.
     */
    @Parameter
    protected JacocoSettings jacoco;

    @Parameter(defaultValue = "${localRepository}")
    protected ArtifactRepository localRepository;

    /**
     * Map of plugin artifacts.
     */
    @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
    protected Map<String, Artifact> pluginArtifactMap;

    /**
     * The Maven project.
     */
    @Parameter(property = "project")
    protected MavenProject project;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}")
    protected List<ArtifactRepository> remoteRepositories;

    /**
     * The folder where the integration test reports will be placed. Please note that the content of this folder will be
     * deleted before running the tests.
     */
    @Parameter(property = "eosgi.testReportFolder", defaultValue = "${project.build.directory}/eosgi-report")
    protected String reportFolder;

    /**
     * Comma separated list of ports of currently running OSGi containers. Such ports are normally opened with
     * richConsole. In case this property is defined, dependency changes will be pushed via the defined ports.
     */
    @Parameter(property = "eosgi.servicePort")
    protected String servicePort;

    /**
     * The directory where there may be additional files to create the distribution package (optional).
     */
    @Parameter(property = "eosgi.sourceDistPath", defaultValue = "${basedir}/src/dist/")
    protected String sourceDistPath;

    protected Map<String, Integer> upgradePortByEnvironmentId = null;

    public DistMojo() {
        try {
            distConfigJAXBContext =
                    JAXBContext.newInstance(ObjectFactory.class.getPackage().getName(),
                            ObjectFactory.class.getClassLoader());
        } catch (JAXBException e) {
            throw new RuntimeException("Could not create JAXB Context for distribution configuration file", e);
        }
    }

    protected void addDefaultSettingsToEnvironment(final EnvironmentConfiguration environment)
            throws MojoExecutionException {
        String environmentId = environment.getId();
        if (environmentId == null) {
            throw new MojoExecutionException("Environment id must not be null");
        }
        Map<String, String> systemProperties = environment.getSystemProperties();
        String currentValue = systemProperties.get(RichConsoleConstants.SYSPROP_ENVIRONMENT_ID);
        if ((currentValue != null) && !currentValue.equals(environmentId)) {
            throw new MojoExecutionException("If defined, the system property "
                    + RichConsoleConstants.SYSPROP_ENVIRONMENT_ID
                    + " must be the same as environment id: " + environment.getId());
        }
        if (currentValue == null) {
            systemProperties.put(RichConsoleConstants.SYSPROP_ENVIRONMENT_ID, environmentId);
        }
    }

    private void checkIfEveryPortCanBeUpdated(final EnvironmentConfiguration[] environments)
            throws MojoExecutionException {
        Map<String, Integer> tmpUpgradePortByEnvironmentId = new HashMap<>(upgradePortByEnvironmentId);
        for (EnvironmentConfiguration environment : environments) {
            tmpUpgradePortByEnvironmentId.remove(environment.getId());
        }
        if (tmpUpgradePortByEnvironmentId.size() > 0) {
            throw new MojoExecutionException("Could not find environment configuration for service ports: "
                    + tmpUpgradePortByEnvironmentId.toString());
        }
    }

    protected void defineUpgradePorts() throws MojoExecutionException {
        upgradePortByEnvironmentId = new HashMap<String, Integer>();
        if (servicePort != null) {
            String[] servicePortArray = servicePort.split(",");
            InetAddress localAddress;
            try {
                localAddress = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                throw new MojoExecutionException("Could not determine local address");
            }
            for (String servicePortString : servicePortArray) {
                Integer servicePort = Integer.valueOf(servicePortString);
                String environmentId = queryEnvironmentIdFromPort(localAddress, servicePort);
                if (environmentId == null) {
                    throw new MojoExecutionException("Could not determine environment id for service port "
                            + servicePort);
                }
                getLog().info("Assigning '" + environmentId + "' to service port " + servicePort);
                upgradePortByEnvironmentId.put(environmentId, servicePort);
            }
        }
    }

    protected void distributeArtifacts(final DistributionPackageType distributionPackage, final File envDistFolderFile,
            final Socket environmentSocket)
            throws MojoExecutionException, IOException {

        ArtifactsType artifactsJaxbObj = distributionPackage.getArtifacts();
        if (artifactsJaxbObj == null) {
            return;
        }
        List<ArtifactType> artifacts = artifactsJaxbObj
                .getArtifact();

        CopyModeType environmentCopyMode = distributionPackage.getCopyMode();
        for (ArtifactType artifact : artifacts) {

            String artifactType = artifact.getType();
            if (artifactType == null) {
                artifactType = "jar";
            }
            Artifact mavenArtifact = null;
            if (artifact.getClassifier() == null) {
                mavenArtifact =
                        artifactFactory.createArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                                artifact.getVersion(), "compile", artifactType);
            } else {
                mavenArtifact =
                        artifactFactory.createArtifactWithClassifier(artifact.getGroupId(), artifact.getArtifactId(),
                                artifact.getVersion(), artifactType, artifact.getClassifier());

            }
            try {
                artifactResolver.resolve(mavenArtifact, remoteRepositories, localRepository);
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException("Could not resolve artifact for creating distribution package", e);
            } catch (ArtifactNotFoundException e) {
                throw new MojoExecutionException("Could not resolve artifact for creating distribution package", e);
            }
            File targetFileFolder = envDistFolderFile;
            if (artifact.getTargetFolder() != null) {
                targetFileFolder = new File(envDistFolderFile, artifact.getTargetFolder());
            }
            targetFileFolder.mkdirs();
            String targetFileName = artifact.getTargetFile();
            if (targetFileName == null) {
                targetFileName = mavenArtifact.getFile().getName();
                artifact.setTargetFile(targetFileName);
            }
            File targetFile = new File(targetFileFolder, targetFileName);

            CopyModeType artifactCopyMode = environmentCopyMode;
            if (artifact.getCopyMode() != null) {
                artifactCopyMode = artifact.getCopyMode();
            }
            boolean fileChanged = fileManager.copyFile(mavenArtifact.getFile(), targetFile, artifactCopyMode);
            if (fileChanged && (environmentSocket != null)) {
                BundleDataType bundle = artifact.getBundle();
                if (bundle != null) {
                    OSGiActionType osgiAction = bundle.getAction();
                    if (!OSGiActionType.NONE.equals(osgiAction)) {
                        String bundleLocation = bundle.getLocation();
                        Integer startLevel = bundle.getStartLevel();
                        StringBuilder sb = new StringBuilder(RichConsoleConstants.TCPCOMMAND_DEPLOY_BUNDLE);
                        sb.append(" ").append(bundleLocation.toString()).append("@");
                        if (startLevel != null) {
                            sb.append(startLevel).append(":");
                        }
                        sb.append("start");
                        String response = PluginUtil.sendCommandToSocket(sb.toString(), environmentSocket,
                                "environment", getLog());
                        if (!RichConsoleConstants.TCPRESPONSE_OK.equals(response)) {
                            throw new MojoExecutionException(
                                    "Environment server did not answer ok after bundle deployment");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        processJacocoSettings();
        defineUpgradePorts();
        fileManager = new FileManager(getLog());
        try {
            List<DistributableArtifact> processedArtifacts;
            File globalDistFolderFile = new File(getDistFolder());

            distributedEnvironments = new ArrayList<DistributedEnvironment>();
            EnvironmentConfiguration[] environmentsToProcess = getEnvironmentsToProcess();
            checkIfEveryPortCanBeUpdated(environmentsToProcess);
            InetAddress localAddress;
            try {
                localAddress = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                throw new MojoExecutionException("Could not query address for localhost", e);
            }
            for (EnvironmentConfiguration environment : environmentsToProcess) {
                try {
                    processedArtifacts = generateDistributableArtifacts(environment);
                } catch (MalformedURLException e) {
                    throw new MojoExecutionException("Could not resolve dependent artifacts of project", e);
                }

                addDefaultSettingsToEnvironment(environment);
                Artifact distPackageArtifact = resolveDistPackage(environment);
                File distPackageFile = distPackageArtifact.getFile();
                File distFolderFile = new File(globalDistFolderFile, environment.getId());

                CopyModeType environmentCopyMode = (getCopyMode() != null) ? CopyModeType.fromValue(getCopyMode())
                        : null;
                DistributionPackageType existingDistConfig = readDistConfig(distFolderFile);

                if (existingDistConfig != null) {
                    environmentCopyMode = existingDistConfig.getCopyMode();
                }
                if (environmentCopyMode == null) {
                    environmentCopyMode = CopyModeType.FILE;
                }
                if (CopyModeType.SYMBOLIC_LINK.equals(environmentCopyMode)
                        && !fileManager.isSystemSymbolicLinkCapable()) {
                    throw new MojoExecutionException(
                            "It seems that the operating system does not support symbolic links");
                }

                Socket environmentSocket = null;
                try (ZipFile distPackageZipFile = new ZipFile(distPackageFile)) {
                    Integer environmentServicePort = upgradePortByEnvironmentId.get(environment.getId());
                    if (environmentServicePort != null) {
                        environmentSocket = new Socket(localAddress, environmentServicePort);
                    }
                    fileManager.unpackZipFile(distPackageFile, distFolderFile);

                    if (sourceDistPath != null) {
                        File sourceDistPathFile = new File(sourceDistPath);
                        if (sourceDistPathFile.exists() && sourceDistPathFile.isDirectory()) {
                            fileManager.copyDirectory(sourceDistPathFile, distFolderFile, environmentCopyMode);
                        }
                    }

                    DistributionPackageType distributionPackage =
                            parseConfiguration(distFolderFile, processedArtifacts, environment,
                                    environmentCopyMode);

                    Map<ArtifactKey, ArtifactType> artifactMap = PluginUtil.createArtifactMap(existingDistConfig);
                    List<ArtifactType> artifactsToRemove = PluginUtil.getArtifactsToRemove(artifactMap,
                            distributionPackage);

                    if (environmentSocket != null) {
                        for (ArtifactType artifactType : artifactsToRemove) {
                            BundleDataType bundle = artifactType.getBundle();
                            if (bundle != null) {
                                String command = RichConsoleConstants.TCPCOMMAND_UNINSTALL + " "
                                        + bundle.getSymbolicName() + ":" + bundle.getVersion();
                                String response = PluginUtil.sendCommandToSocket(command, environmentSocket,
                                        "environment", getLog());
                                if (!RichConsoleConstants.TCPRESPONSE_OK.equals(response)) {
                                    throw new MojoExecutionException(
                                            "Environment server did not answer ok after bundle deployment");
                                }
                            }
                        }
                    }
                    for (ArtifactType artifactType : artifactsToRemove) {
                        String targetFolder = artifactType.getTargetFolder();
                        File targetFolderFile = distFolderFile;
                        if (targetFolder != null) {
                            targetFolderFile = new File(distFolderFile, targetFolder);
                        }
                        String targetFile = artifactType.getTargetFile();
                        if (targetFile == null) {
                            targetFile = artifactType.getArtifactId() + "-" + artifactType.getVersion();
                            if (artifactType.getClassifier() != null) {
                                targetFile += "-" + artifactType.getClassifier();
                            }
                            targetFile += "." + artifactType.getType();
                        }

                        File artifactFile = new File(targetFolderFile, targetFile);
                        artifactFile.delete();
                    }

                    distributeArtifacts(distributionPackage, distFolderFile, environmentSocket);

                    parseParseables(distributionPackage, distFolderFile, processedArtifacts, environment);
                    distributedEnvironments.add(new DistributedEnvironment(environment, distributionPackage,
                            distFolderFile, processedArtifacts));

                } catch (IOException e) {
                    throw new MojoExecutionException("Could not uncompress distribution package file: "
                            + distPackageFile.toString(), e);
                } finally {
                    if (environmentSocket != null) {
                        try {
                            environmentSocket.close();
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error during closing socket for environment "
                                    + environment.getId(), e);
                        }
                    }
                }
            }
        } finally {
            try {
                fileManager.close();
            } catch (IOException e) {
                getLog().error("Could not close file manager", e);
            }
        }
    }

    public BundleSettings findMatchingSettings(final EnvironmentConfiguration environment, final String symbolicName,
            final String bundleVersion) {
        // Getting the start level
        List<BundleSettings> bundleSettingsList = environment.getBundleSettings();
        Iterator<BundleSettings> iterator = bundleSettingsList.iterator();
        BundleSettings matchedSettings = null;
        while (iterator.hasNext() && (matchedSettings == null)) {
            BundleSettings settings = iterator.next();
            if (settings.getSymbolicName().equals(symbolicName)
                    && ((settings.getVersion() == null) || settings.getVersion().equals(bundleVersion))) {
                matchedSettings = settings;
            }
        }
        return matchedSettings;
    }

    /**
     * Getting the processed artifacts of the project. The artifact list is calculated each time when the function is
     * called therefore the developer should not call it inside an iteration.
     *
     * @param environment
     *            Configuration of the environment that the distributable artifacts will be generated for.
     * @return The list of dependencies that are OSGI bundles but do not have the scope "provided"
     * @throws MalformedURLException
     *             if the URL for the artifact is broken.
     */
    protected List<DistributableArtifact> generateDistributableArtifacts(final EnvironmentConfiguration environment)
            throws MalformedURLException {
        @SuppressWarnings("unchecked")
        List<Artifact> availableArtifacts = new ArrayList<Artifact>(project.getArtifacts());
        if (executedProject != null) {
            availableArtifacts.add(executedProject.getArtifact());
        } else {
            availableArtifacts.add(project.getArtifact());
        }

        List<DistributableArtifact> result = new ArrayList<DistributableArtifact>();
        for (Artifact artifact : availableArtifacts) {
            if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
                DistributableArtifact processedArtifact = processArtifact(environment, artifact);
                result.add(processedArtifact);
            }
        }
        return result;
    }

    public String getCopyMode() {
        return copyMode;
    }

    protected EnvironmentConfiguration getDefaultEnvironment() {
        getLog().info("There is no environment specified in the project. Creating felix environment with"
                + " default settings");
        EnvironmentConfiguration defaultEnvironment = new EnvironmentConfiguration();
        defaultEnvironment.setId("equinox");
        defaultEnvironment.setFramework("equinox");
        return defaultEnvironment;
    }

    public String getDistFolder() {
        return distFolder;
    }

    public List<DistributedEnvironment> getDistributedEnvironments() {
        return distributedEnvironments;
    }

    public EnvironmentConfiguration[] getEnvironments() {
        if ((environments == null) || (environments.length == 0)) {
            environments = new EnvironmentConfiguration[] { getDefaultEnvironment() };
        }
        return environments;
    }

    /**
     * Getting an array of the environment configurations that should be processed based on the value of the
     * {@link #environmentId} parameter. The value, that is returned, is calculated the first time the function is
     * called.
     *
     * @return The array of environment ids that should be processed.
     */
    protected EnvironmentConfiguration[] getEnvironmentsToProcess() {
        if (environmentsToProcess != null) {
            return environmentsToProcess;
        }

        if ("*".equals(environmentId)) {
            environmentsToProcess = getEnvironments();
        } else {
            String[] environmentIdArray = environmentId.trim().split(",");

            EnvironmentConfiguration[] tmpEnvironments = getEnvironments();

            List<EnvironmentConfiguration> result = new ArrayList<EnvironmentConfiguration>();
            for (EnvironmentConfiguration tmpEnvironment : tmpEnvironments) {
                boolean found = false;
                int j = 0, n = environmentIdArray.length;
                while (!found && (j < n)) {
                    if (environmentIdArray[j].equals(tmpEnvironments[j].getId())) {
                        found = true;
                        result.add(tmpEnvironment);
                    }
                    j++;
                }
            }
            environmentsToProcess = result.toArray(new EnvironmentConfiguration[result.size()]);
        }
        return environmentsToProcess;
    }

    public JacocoSettings getJacoco() {
        return jacoco;
    }

    protected DistributionPackageType parseConfiguration(final File distFolderFile,
            final List<DistributableArtifact> distributableArtifacts, final EnvironmentConfiguration environment,
            final CopyModeType environmentCopyMode)
            throws MojoExecutionException {
        File configFile = new File(distFolderFile, "/.eosgi.dist.xml");

        VelocityContext context = new VelocityContext();
        context.put("distributableArtifacts", distributableArtifacts);
        context.put("environment", environment);
        context.put("copyMode", environmentCopyMode.value());
        context.put("escapeTool", new EscapeTool());
        context.put("distUtil", new DistUtil());
        try {
            fileManager.replaceFileWithParsed(configFile, context, "UTF8");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not run velocity on configuration file: " + configFile.getName(), e);
        }
        return readDistConfig(distFolderFile);
    }

    protected void parseParseables(final DistributionPackageType distributionPackage, final File distFolderFile,
            final List<DistributableArtifact> distributableArtifacts, final EnvironmentConfiguration environment)
            throws MojoExecutionException {
        VelocityContext context = new VelocityContext();
        context.put("distributableArtifacts", distributableArtifacts);
        context.put("distributionPackage", distributionPackage);
        context.put("environment", environment);
        context.put("escapeTool", new EscapeTool());
        context.put("distUtil", new DistUtil());
        ParseablesType parseables = distributionPackage.getParseables();
        if (parseables != null) {
            List<ParseableType> parseable = parseables.getParseable();
            for (ParseableType p : parseable) {
                String path = p.getPath();
                File parseableFile = new File(distFolderFile, path);
                if (!parseableFile.exists()) {
                    throw new MojoExecutionException("File that should be parsed does not exist: "
                            + parseableFile.getAbsolutePath());
                }
                try {
                    fileManager.replaceFileWithParsed(parseableFile, context, p.getEncoding());
                } catch (IOException e) {
                    throw new MojoExecutionException("Could not replace parseable with parsed content: " + p.getPath(),
                            e);
                }
            }
        }
    }

    /**
     * Checking if an artifact is an OSGI bundle. An artifact is an OSGI bundle if the MANIFEST.MF file inside contains
     * a Bundle-SymbolicName.
     *
     * @param environment
     *            The environment that uses the artifact.
     * @param artifact
     *            The artifact that is checked.
     * @return A {@link DistributableArtifact} with the Bundle-SymbolicName and a Bundle-Version. Bundle-Version comes
     *         from MANIFEST.MF but if Bundle-Version is not available there the default 0.0.0 version is provided.
     */
    public DistributableArtifact processArtifact(final EnvironmentConfiguration environment,
            final org.apache.maven.artifact.Artifact artifact) {

        if ("pom".equals(artifact.getType())) {
            return new DistributableArtifact(artifact, null, null);
        }
        File artifactFile = artifact.getFile();
        if ((artifactFile == null) || !artifactFile.exists()) {
            return new DistributableArtifact(artifact, null, null);
        }
        Manifest manifest = null;

        try (JarFile jarFile = new JarFile(artifactFile)) {
            manifest = jarFile.getManifest();
            if (manifest == null) {
                return new DistributableArtifact(artifact, null, null);
            }

            Attributes mainAttributes = manifest.getMainAttributes();
            String symbolicName = mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
            String version = mainAttributes.getValue(Constants.BUNDLE_VERSION);

            DistributableArtifactBundleMeta bundleData = null;
            if ((symbolicName != null) && (version != null)) {
                int semicolonIndex = symbolicName.indexOf(';');
                if (semicolonIndex >= 0) {
                    symbolicName = symbolicName.substring(0, semicolonIndex);
                }

                version = PluginUtil.normalizeVersion(version);

                String fragmentHost = mainAttributes.getValue(Constants.FRAGMENT_HOST);
                String importPackage = mainAttributes.getValue(Constants.IMPORT_PACKAGE);
                String exportPackage = mainAttributes.getValue(Constants.EXPORT_PACKAGE);
                BundleSettings bundleSettings = findMatchingSettings(environment, symbolicName, version);
                Integer startLevel = null;
                if (bundleSettings != null) {
                    startLevel = bundleSettings.getStartLevel();
                }

                bundleData = new DistributableArtifactBundleMeta(symbolicName, version, fragmentHost, importPackage,
                        exportPackage, startLevel);
            }

            return new DistributableArtifact(artifact, manifest, bundleData);
        } catch (IOException e) {
            return new DistributableArtifact(artifact, null, null);
        }
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

            if (jacoco.getOutput() != null) {
                sb.append(",output=").append(jacoco.getOutput());

            }
            if (jacoco.getAddress() != null) {
                sb.append(",address=").append(jacoco.getAddress());
            }
            if (jacoco.getPort() != null) {
                sb.append(",port=").append(jacoco.getPort());
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

    protected String queryEnvironmentIdFromPort(final InetAddress address, final int port)
            throws MojoExecutionException {
        try (Socket socket = new Socket(address, port)) {
            String response = PluginUtil.sendCommandToSocket(RichConsoleConstants.TCPCOMMAND_GET_ENVIRONMENT_ID,
                    socket, "environment", getLog());

            if ((response == null) || response.trim().equals("")) {
                return null;
            }
            return response;

        } catch (IOException e) {
            throw new MojoExecutionException("Could not connect to service port of environment: " + address.toString()
                    + ":" + port);
        }
    }

    /**
     * Reading up the content of each /META-INF/eosgi-frameworks.properties file from the classpath of the plugin.
     *
     * @return The merged properties file.
     * @throws IOException
     *             if a read error occurs.
     */
    protected Properties readDefaultFrameworkPops() throws IOException {
        Enumeration<URL> resources =
                this.getClass().getClassLoader().getResources("META-INF/eosgi-frameworks.properties");
        Properties result = new Properties();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            Properties tmpProps = new Properties();
            InputStream inputStream = resource.openStream();
            try {
                tmpProps.load(inputStream);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
            result.putAll(tmpProps);
        }
        return result;
    }

    protected DistributionPackageType readDistConfig(final File distFolderFile) throws MojoExecutionException {
        File distConfigFile = new File(distFolderFile, "/.eosgi.dist.xml");
        if (distConfigFile.exists()) {
            try {
                Unmarshaller unmarshaller = distConfigJAXBContext.createUnmarshaller();
                Object distributionPackage = unmarshaller.unmarshal(distConfigFile);
                if (distributionPackage instanceof JAXBElement) {

                    @SuppressWarnings("unchecked")
                    JAXBElement<DistributionPackageType> jaxbDistPack =
                            (JAXBElement<DistributionPackageType>) distributionPackage;
                    distributionPackage = jaxbDistPack.getValue();
                }
                if (distributionPackage instanceof DistributionPackageType) {
                    return (DistributionPackageType) distributionPackage;
                } else {
                    throw new MojoExecutionException(
                            "The root element in the provided distribution configuration file "
                                    + "is not the expected DistributionPackage element");
                }
            } catch (JAXBException e) {
                throw new MojoExecutionException("Failed to process already existing distribution configuration file: "
                        + distConfigFile.getAbsolutePath(), e);
            }
        } else {
            return null;
        }
    }

    protected Artifact resolveDistPackage(final EnvironmentConfiguration environment) throws MojoExecutionException {
        String[] distPackageIdParts;
        try {
            distPackageIdParts = resolveDistPackageId(environment);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not get distribution package", e);
        }
        Artifact distPackageArtifact =
                artifactFactory.createArtifact(distPackageIdParts[0], distPackageIdParts[1], distPackageIdParts[2],
                        "compile", "zip");

        try {
            artifactResolver.resolve(distPackageArtifact, remoteRepositories, localRepository);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Could not resolve distribution artifact: "
                    + distPackageArtifact.getArtifactId(), e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Could not resolve distribution artifact: "
                    + distPackageArtifact.getArtifactId(), e);
        }
        return distPackageArtifact;
    }

    /**
     *
     * @return A three length string array that contains the groupId, artifactId and version of the dist package.
     * @throws IOException
     *             if the resrources of the default framework id configurations cannot be read.
     * @throws MojoExecutionException
     *             if the distPackage expression configured for this plugin has wrong format.
     */
    protected String[] resolveDistPackageId(final EnvironmentConfiguration environment) throws IOException,
            MojoExecutionException {
        String frameworkArtifact = environment.getFramework();
        String[] distPackageParts = frameworkArtifact.split("\\:");
        if (distPackageParts.length == 1) {
            Properties defaultFrameworkPops = readDefaultFrameworkPops();
            String defaultFrameworkDistPackage = defaultFrameworkPops.getProperty(frameworkArtifact);
            if (defaultFrameworkDistPackage == null) {
                getLog().error(
                        "Could not find entry in any of the /META-INF/eosgi-frameworks.properites configuration "
                                + "files on the classpath for the framework id " + frameworkArtifact);
                throw new MojoExecutionException("Could not find framework dist package [" + frameworkArtifact + "]");
            } else {
                distPackageParts = defaultFrameworkDistPackage.split("\\:");
                getLog().info(
                        "Dist package definition '" + frameworkArtifact + "'  was resolved to be '"
                                + defaultFrameworkDistPackage + "'");
            }
        }
        if (distPackageParts.length != 3) {
            throw new MojoExecutionException("Invalid distribution package id format: " + frameworkArtifact);
        }
        return distPackageParts;
    }

    public void setJacoco(JacocoSettings jacoco) {
        this.jacoco = jacoco;
    }

}
