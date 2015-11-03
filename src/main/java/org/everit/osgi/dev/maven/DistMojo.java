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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.everit.osgi.dev.eosgi.dist.schema.util.DistSchemaProvider;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactsType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.DistributionPackageType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.OSGiActionType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ParsableType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ParsablesType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.UseByType;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.dto.DistributableArtifact;
import org.everit.osgi.dev.maven.dto.DistributedEnvironment;
import org.everit.osgi.dev.maven.util.ArtifactKey;
import org.everit.osgi.dev.maven.util.DistUtil;
import org.everit.osgi.dev.maven.util.FileManager;
import org.everit.osgi.dev.maven.util.PluginUtil;
import org.everit.osgi.dev.richconsole.RichConsoleConstants;

/**
 * Creates a distribution package for the project. Distribution packages may be provided as
 * Environment parameters or 'equinox', the default option, -may also be used. The structure of the
 * distribution package may be different for different types.
 */
@Mojo(name = "dist", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true,
    requiresDependencyResolution = ResolutionScope.COMPILE)
@Execute(phase = LifecyclePhase.PACKAGE)
public class DistMojo extends AbstractEOSGiMojo {

  private static final int MAVEN_ARTIFACT_ID_PART_NUM = 3;

  @Component
  protected ArtifactFactory artifactFactory;

  @Component
  protected ArtifactRepositoryFactory artifactRepositoryFactory;

  @Component
  protected ArtifactResolver artifactResolver;

  /**
   * Path to folder where the distribution will be generated. The content of this folder will be
   * overridden if the files with same name already exist.
   *
   */
  @Parameter(property = "eosgi.distFolder", defaultValue = "${project.build.directory}/eosgi-dist")
  protected String distFolder;

  protected List<DistributedEnvironment> distributedEnvironments;

  protected DistSchemaProvider distSchemaProvider = new DistSchemaProvider();

  private FileManager fileManager = null;

  @Parameter(defaultValue = "${localRepository}")
  protected ArtifactRepository localRepository;

  /**
   * Map of plugin artifacts.
   */
  @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
  protected Map<String, Artifact> pluginArtifactMap;

  @Parameter(defaultValue = "${project.remoteArtifactRepositories}")
  protected List<ArtifactRepository> remoteRepositories;

  /**
   * The folder where the integration test reports will be placed. Please note that the content of
   * this folder will be deleted before running the tests.
   */
  @Parameter(property = "eosgi.testReportFolder",
      defaultValue = "${project.build.directory}/eosgi-report")
  protected String reportFolder;

  /**
   * Comma separated list of ports of currently running OSGi containers. Such ports are normally
   * opened with richConsole. In case this property is defined, dependency changes will be pushed
   * via the defined ports.
   */
  @Parameter(property = "eosgi.servicePort")
  protected String servicePort;

  /**
   * The directory where there may be additional files to create the distribution package
   * (optional).
   */
  @Parameter(property = "eosgi.sourceDistPath", defaultValue = "${basedir}/src/dist/")
  protected String sourceDistPath;

  protected Map<String, Integer> upgradePortByEnvironmentId = null;

  private void addDefaultSettingsToEnvironment(final EnvironmentConfiguration environment)
      throws MojoExecutionException {
    String environmentId = environment.getId();
    if (environmentId == null) {
      throw new MojoExecutionException("Environment id must not be null");
    }
    Map<String, String> systemProperties = environment.getLaunchConfig().getSystemProperties();
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
      throw new MojoExecutionException(
          "Could not find environment configuration for service ports: "
              + tmpUpgradePortByEnvironmentId.toString());
    }
  }

  /**
   * Defines the ports for each environment where upgrade should be executed.
   *
   * @throws MojoExecutionException
   *           in case the environment cannot be determined based on the port number.
   */
  private void defineUpgradePorts() throws MojoExecutionException {
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

  private void distributeArtifact(final File envDistFolderFile, final Socket environmentSocket,
      final ArtifactType artifact)
          throws MojoExecutionException, IOException {
    Artifact mavenArtifact = resolveMavenArtifactByArtifactType(artifact);
    downloadArtifactIfNecessary(mavenArtifact);

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

    boolean fileChanged = fileManager.overCopyFile(mavenArtifact.getFile(), targetFile);
    if (fileChanged && (environmentSocket != null)) {
      BundleDataType bundle = artifact.getBundle();
      if (bundle != null) {
        OSGiActionType osgiAction = bundle.getAction();
        if (!OSGiActionType.NONE.equals(osgiAction)) {
          String bundleLocation = bundle.getLocation();
          Integer startLevel = bundle.getStartLevel();
          StringBuilder sb = new StringBuilder(RichConsoleConstants.TCPCOMMAND_DEPLOY_BUNDLE);
          sb.append(" ").append(bundleLocation).append("@");
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

  private void distributeArtifacts(final ArtifactsType artifactsJaxbObj,
      final File envDistFolderFile,
      final Socket environmentSocket)
          throws MojoExecutionException, IOException {

    if (artifactsJaxbObj == null) {
      return;
    }
    List<ArtifactType> artifacts = artifactsJaxbObj.getArtifact();

    for (ArtifactType artifact : artifacts) {
      distributeArtifact(envDistFolderFile, environmentSocket, artifact);
    }
  }

  private void downloadArtifactIfNecessary(final Artifact mavenArtifact)
      throws MojoExecutionException {
    try {
      artifactResolver.resolve(mavenArtifact, remoteRepositories, localRepository);
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException(
          "Could not resolve artifact for creating distribution package", e);
    } catch (ArtifactNotFoundException e) {
      throw new MojoExecutionException(
          "Could not resolve artifact for creating distribution package", e);
    }
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    defineUpgradePorts();

    fileManager = new FileManager();
    File globalDistFolderFile = new File(distFolder);

    distributedEnvironments = new ArrayList<DistributedEnvironment>();
    EnvironmentConfiguration[] environmentsToProcess = getEnvironmentsToProcess();
    checkIfEveryPortCanBeUpdated(environmentsToProcess);
    InetAddress localAddress = resolveLocalInetAddress();

    for (EnvironmentConfiguration environment : environmentsToProcess) {
      executeOnEnvironment(globalDistFolderFile, localAddress, environment);
    }
  }

  private void executeOnEnvironment(final File globalDistFolderFile,
      final InetAddress localAddress, final EnvironmentConfiguration environment)
          throws MojoExecutionException {

    List<DistributableArtifact> processedArtifacts;
    try {
      processedArtifacts = generateDistributableArtifacts(environment.getBundleSettings());
    } catch (MalformedURLException e) {
      throw new MojoExecutionException("Could not resolve dependent artifacts of project", e);
    }

    addDefaultSettingsToEnvironment(environment);
    Artifact distPackageArtifact = resolveDistPackage(environment.getFramework());
    File distPackageFile = distPackageArtifact.getFile();
    File distFolderFile = new File(globalDistFolderFile, environment.getId());

    DistributionPackageType existingDistConfig = distSchemaProvider.readDistConfig(distFolderFile);

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
          fileManager.copyDirectory(sourceDistPathFile, distFolderFile);
        }
      }

      parseConfiguration(distFolderFile, processedArtifacts, environment);

      DistributionPackageType distributionPackage =
          distSchemaProvider.readDistConfig(distFolderFile);
      ArtifactsType artifacts = distributionPackage.getArtifacts();

      Map<ArtifactKey, ArtifactType> artifactMap =
          PluginUtil.createArtifactMap(existingDistConfig);
      List<ArtifactType> artifactsToRemove =
          PluginUtil.getArtifactsToRemove(artifactMap, artifacts);

      if (environmentSocket != null) {
        removeArtifactsRemotely(environmentSocket, artifactsToRemove);
      }

      removeArtifactsFromDistributedEnvironment(distFolderFile, artifactsToRemove);

      distributeArtifacts(artifacts, distFolderFile, environmentSocket);

      parseParsables(distFolderFile);
      distributedEnvironments.add(
          new DistributedEnvironment(environment, distributionPackage,
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

  /**
   * Parses the configuration of a distribution package.
   */
  private void parseConfiguration(
      final File distFolderFile,
      final List<DistributableArtifact> distributableArtifacts,
      final EnvironmentConfiguration environment)
          throws MojoExecutionException {

    File configFile = new File(distFolderFile, "/.eosgi.dist.xml");

    Map<String, Object> vars = new HashMap<>();
    vars.put("distributableArtifacts", distributableArtifacts);
    vars.put("environment", environment);
    vars.put("mainJar", "org.eclipse.osgi_3.10.100.v20150529-1857.jar"); // TODO mainJar
    vars.put("mainClass", "org.eclipse.core.runtime.adaptor.EclipseStarter"); // TODO mainClass
    vars.put("classPath", ""); // TODO classPath
    vars.put("distUtil", new DistUtil());
    try {
      fileManager.replaceFileWithParsed(configFile, vars, "UTF8");
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Could not run velocity on configuration file: " + configFile.getName(), e);
    }
  }

  /**
   * Parses and processes the files that are templates.
   */
  private void parseParsables(final File distFolderFile)
      throws MojoExecutionException {

    DistributionPackageType distributionPackage =
        distSchemaProvider.geOverridedDistributionPackage(distFolderFile, UseByType.PARSABLES);

    Map<String, Object> vars = new HashMap<>();
    vars.put("distributionPackage", distributionPackage);
    vars.put("distUtil", new DistUtil());

    ParsablesType parsables = distributionPackage.getParsables();
    if (parsables != null) {

      List<ParsableType> parsable = parsables.getParsable();

      for (ParsableType p : parsable) {

        String path = p.getPath();
        File parsableFile = new File(distFolderFile, path);

        if (!parsableFile.exists()) {
          throw new MojoExecutionException("File that should be parsed does not exist: "
              + parsableFile.getAbsolutePath());
        }

        try {
          fileManager.replaceFileWithParsed(parsableFile, vars, p.getEncoding());
        } catch (IOException e) {
          throw new MojoExecutionException(
              "Could not replace parsable with parsed content: " + p.getPath(), e);
        }
      }
    }
  }

  private String queryEnvironmentIdFromPort(final InetAddress address, final int port)
      throws MojoExecutionException {
    try (Socket socket = new Socket(address, port)) {
      String response =
          PluginUtil.sendCommandToSocket(RichConsoleConstants.TCPCOMMAND_GET_ENVIRONMENT_ID,
              socket, "environment", getLog());

      if ((response == null) || "".equals(response.trim())) {
        return null;
      }
      return response;

    } catch (IOException e) {
      throw new MojoExecutionException(
          "Could not connect to service port of environment: " + address.toString()
              + ":" + port);
    }
  }

  /**
   * Reading up the content of each /META-INF/eosgi-frameworks.properties file from the classpath of
   * the plugin.
   *
   * @return The merged properties file.
   * @throws IOException
   *           if a read error occurs.
   */
  private Properties readDefaultFrameworkPops() throws IOException {
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

  private void removeArtifactsFromDistributedEnvironment(final File distFolderFile,
      final List<ArtifactType> artifactsToRemove) {
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
  }

  private void removeArtifactsRemotely(final Socket environmentSocket,
      final List<ArtifactType> artifactsToRemove)
          throws IOException, MojoExecutionException {
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

  private Artifact resolveDistPackage(final String frameworkArtifact)
      throws MojoExecutionException {
    String[] distPackageIdParts;
    try {
      distPackageIdParts = resolveDistPackageId(frameworkArtifact);
    } catch (IOException e) {
      throw new MojoExecutionException("Could not get distribution package", e);
    }
    Artifact distPackageArtifact =
        artifactFactory.createArtifact(distPackageIdParts[0], distPackageIdParts[1],
            distPackageIdParts[2],
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
   * Resolves the maven artifact id of the distribution package that should be used.
   *
   * @return A three length string array that contains the groupId, artifactId and version of the
   *         dist package.
   * @throws IOException
   *           if the resrources of the default framework id configurations cannot be read.
   * @throws MojoExecutionException
   *           if the distPackage expression configured for this plugin has wrong format.
   */
  private String[] resolveDistPackageId(final String frameworkArtifact)
      throws IOException,
      MojoExecutionException {
    String[] distPackageParts = frameworkArtifact.split("\\:");
    if (distPackageParts.length == 1) {
      Properties defaultFrameworkPops = readDefaultFrameworkPops();
      String defaultFrameworkDistPackage = defaultFrameworkPops.getProperty(frameworkArtifact);
      if (defaultFrameworkDistPackage == null) {
        getLog().error(
            "Could not find entry in any of the "
                + "/META-INF/eosgi-frameworks.properites configuration "
                + "files on the classpath for the framework id " + frameworkArtifact);
        throw new MojoExecutionException(
            "Could not find framework dist package [" + frameworkArtifact + "]");
      } else {
        distPackageParts = defaultFrameworkDistPackage.split("\\:");
        getLog().info(
            "Dist package definition '" + frameworkArtifact + "'  was resolved to be '"
                + defaultFrameworkDistPackage + "'");
      }
    }
    if (distPackageParts.length != MAVEN_ARTIFACT_ID_PART_NUM) {
      throw new MojoExecutionException(
          "Invalid distribution package id format: " + frameworkArtifact);
    }
    return distPackageParts;
  }

  private InetAddress resolveLocalInetAddress() throws MojoExecutionException {
    InetAddress localAddress;
    try {
      localAddress = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new MojoExecutionException("Could not query address for localhost", e);
    }
    return localAddress;
  }

  private Artifact resolveMavenArtifactByArtifactType(final ArtifactType artifact) {
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
          artifactFactory.createArtifactWithClassifier(artifact.getGroupId(),
              artifact.getArtifactId(),
              artifact.getVersion(), artifactType, artifact.getClassifier());

    }
    return mavenArtifact;
  }

}
