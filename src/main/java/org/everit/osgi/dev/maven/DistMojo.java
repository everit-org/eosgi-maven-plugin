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
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.ReflectionException;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.everit.osgi.dev.eosgi.dist.schema.util.DistSchemaProvider;
import org.everit.osgi.dev.eosgi.dist.schema.util.LaunchConfigurationDTO;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactsType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.DistributionPackageType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.OSGiActionType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ParsableType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ParsablesType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.UseByType;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.configuration.LaunchConfig;
import org.everit.osgi.dev.maven.configuration.LaunchConfigOverride;
import org.everit.osgi.dev.maven.dto.DistributableArtifact;
import org.everit.osgi.dev.maven.dto.DistributedEnvironment;
import org.everit.osgi.dev.maven.upgrade.NoopRemoteOSGiManager;
import org.everit.osgi.dev.maven.upgrade.RemoteOSGiManager;
import org.everit.osgi.dev.maven.upgrade.jmx.JMXOSGiManager;
import org.everit.osgi.dev.maven.upgrade.jmx.JMXOSGiManagerProvider;
import org.everit.osgi.dev.maven.util.DistUtil;
import org.everit.osgi.dev.maven.util.EnvironmentCleaner;
import org.everit.osgi.dev.maven.util.FileManager;
import org.everit.osgi.dev.maven.util.PluginUtil;

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

  /**
   * Additional system property that is applied to each environment automatically.
   */
  public static final String SYSPROP_ENVIRONMENT_ID = "org.everit.osgi.dev.environmentId";

  private static final String VAR_DIST_UTIL = "distUtil";

  /**
   * Path to folder where the distribution will be generated. The content of this folder will be
   * overridden if the files with same name already exist.
   *
   */
  @Parameter(property = "eosgi.distFolder", defaultValue = "${project.build.directory}/eosgi-dist")
  protected String distFolder;

  protected List<DistributedEnvironment> distributedEnvironments;

  protected DistSchemaProvider distSchemaProvider = new DistSchemaProvider();

  private JMXOSGiManagerProvider jMXOSGiManagerProvider;

  @Parameter(defaultValue = "${localRepository}")
  protected ArtifactRepository localRepository;

  /**
   * Map of plugin artifacts.
   */
  @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
  protected Map<String, Artifact> pluginArtifactMap;

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
  @Parameter(property = "eosgi.sourceDistFolder", defaultValue = "${basedir}/src/dist/")
  protected String sourceDistFolder;

  private void checkAndAddReservedLaunchConfigurationProperties(
      final EnvironmentConfiguration environment, final LaunchConfig launchConfig)
      throws MojoFailureException {

    checkReservedSystemPropertyInSystemProperties(launchConfig.getSystemProperties());
    LaunchConfigOverride[] overrides = launchConfig.getOverrides();
    for (LaunchConfigOverride launchConfigOverride : overrides) {
      checkReservedSystemPropertyInSystemProperties(launchConfigOverride.getSystemProperties());
    }

    launchConfig.getSystemProperties().put(SYSPROP_ENVIRONMENT_ID, environment.getId());
  }

  private void checkReservedSystemPropertyInSystemProperties(
      final Map<String, String> systemProperties) throws MojoFailureException {
    if (systemProperties != null && systemProperties.containsKey(SYSPROP_ENVIRONMENT_ID)) {
      throw new MojoFailureException("Reserved system property '" + SYSPROP_ENVIRONMENT_ID
          + "' cannot be specified in launchConfig manually");
    }
  }

  private void copyDistFolderToTargetIfExists(final File environmentRootFolder,
      final FileManager fileManager)

      throws MojoExecutionException {
    if (sourceDistFolder != null) {
      File sourceDistPathFile = new File(sourceDistFolder);
      if (sourceDistPathFile.exists() && sourceDistPathFile.isDirectory()) {
        fileManager.copyDirectory(sourceDistPathFile, environmentRootFolder);
      }
    }
  }

  private RemoteOSGiManager createRemoteOSGiManager(final String environmentId,
      final File distFolderFile, final DistributionPackageType existingDistributionPackage) {

    String jmxLocalURL =
        jMXOSGiManagerProvider.getJmxURLForEnvironment(environmentId, distFolderFile);

    if (jmxLocalURL == null) {
      return new NoopRemoteOSGiManager();
    }

    if (existingDistributionPackage == null) {
      getLog().warn(
          "Found JVM that belongs to Environment, but there is no existing distribution package."
              + " The OSGi container will not be upgraded. Restarting of the container is truly"
              + " suggested after running this goal.");
      return new NoopRemoteOSGiManager();
    }

    try {
      return new JMXOSGiManager(jmxLocalURL, getLog());
    } catch (IOException | InstanceNotFoundException | IntrospectionException
        | ReflectionException e) {

      getLog().warn(
          "Could not connect to the running JVM of the environment or apply the changes."
              + " Either install the OSGi JMX Bundle or stop the environment before each"
              + " upgrade! If the JMX Bundle is installed, try solving the cause of this exception!"
              + " EnvironmentId: " + environmentId,
          e);
    }

    return new NoopRemoteOSGiManager();
  }

  private void distributeArtifacts(final String environmentId,
      final RemoteOSGiManager remoteOSGiManager,
      final File envDistFolderFile, final Map<String, ArtifactType> existingArtifactMap,
      final ArtifactsType artifactsType, final FileManager fileManager)
      throws MojoExecutionException {

    if (artifactsType == null) {
      return;
    }

    List<ArtifactType> artifacts = artifactsType.getArtifact();

    List<BundleDataType> bundlesToUpdate = new ArrayList<>();
    List<BundleDataType> bundlesToInstall = new ArrayList<>();

    for (ArtifactType artifact : artifacts) {

      Artifact mavenArtifact = resolveMavenArtifactByArtifactType(artifact);

      File targetFile = PluginUtil.resolveArtifactAbsoluteFile(artifact, envDistFolderFile);

      boolean fileChanged = fileManager.overCopyFile(mavenArtifact.getFile(), targetFile);

      if (fileChanged) {

        BundleDataType bundleDataType = artifact.getBundle();

        if (bundleDataType != null) {
          if (existingArtifactMap.containsKey(bundleDataType.getLocation())) {
            bundlesToUpdate.add(bundleDataType);
          } else {
            bundlesToInstall.add(bundleDataType);
          }
        }
      }

    }

    if (remoteOSGiManager == null) {
      return;
    }

    remoteOSGiManager.installBundles(bundlesToInstall.toArray(new BundleDataType[] {}));
    remoteOSGiManager.updateBundles(bundlesToUpdate.toArray(new BundleDataType[] {}));
    getLog()
        .info("Incremental update of environment [" + environmentId + "] finished successfully.");
  }

  @Override
  protected void doExecute() throws MojoExecutionException, MojoFailureException {

    jMXOSGiManagerProvider = new JMXOSGiManagerProvider(getLog());

    File globalDistFolderFile = new File(distFolder);

    distributedEnvironments = new ArrayList<DistributedEnvironment>();
    EnvironmentConfiguration[] environmentsToProcess = getEnvironmentsToProcess();

    for (EnvironmentConfiguration environment : environmentsToProcess) {
      executeOnEnvironment(globalDistFolderFile, environment);
    }
  }

  private void executeOnEnvironment(final File globalDistFolderFile,
      final EnvironmentConfiguration environment)
      throws MojoExecutionException, MojoFailureException {

    FileManager fileManager = new FileManager();

    List<DistributableArtifact> processedArtifacts =
        generateDistributableArtifacts(environment.getBundleSettings());

    String environmentId = environment.getId();

    Artifact distPackageArtifact = resolveDistPackage(environment.getFramework());
    File distPackageFile = distPackageArtifact.getFile();
    File environmentRootFolder = new File(globalDistFolderFile, environmentId);

    DistributionPackageType existingDistributionPackage =
        distSchemaProvider.getOverriddenDistributionPackage(environmentRootFolder,
            UseByType.PARSABLES);

    LaunchConfigurationDTO existingEnvConfig = distSchemaProvider.getLaunchConfiguration(
        existingDistributionPackage);

    fileManager.unpackZipFile(distPackageFile, environmentRootFolder);
    copyDistFolderToTargetIfExists(environmentRootFolder, fileManager);

    parseConfiguration(environmentRootFolder, processedArtifacts, environment, fileManager);

    DistributionPackageType distributionPackage =
        distSchemaProvider.getOverriddenDistributionPackage(environmentRootFolder,
            UseByType.PARSABLES);

    LaunchConfigurationDTO envConfig = distSchemaProvider.getLaunchConfiguration(
        distributionPackage);

    ArtifactsType artifacts = distributionPackage.getArtifacts();

    Map<String, ArtifactType> existingBundleMap =
        PluginUtil.createBundleMap(existingDistributionPackage);
    List<ArtifactType> bundlesToRemove =
        PluginUtil.getBundlesToRemove(existingBundleMap, artifacts);

    try (RemoteOSGiManager remoteOSGiManager =
        createRemoteOSGiManager(environmentId, environmentRootFolder,
            existingDistributionPackage)) {

      remoteOSGiManager.uninstallBundles(resolveBundlesToUninstall(bundlesToRemove));

      distributeArtifacts(environmentId,
          remoteOSGiManager, environmentRootFolder, existingBundleMap, artifacts, fileManager);

      parseParsables(environmentRootFolder, distributionPackage, fileManager);
      distributedEnvironments.add(
          new DistributedEnvironment(environment, distributionPackage,
              environmentRootFolder, processedArtifacts));

      if (envConfig.isChanged(existingEnvConfig)) {
        getLog().warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        getLog().warn("!!! The environment configuration has been changed. "
            + "[" + environmentId + "] should be restarted.");
        getLog().warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      }

      if (remoteOSGiManager != null) {
        remoteOSGiManager.refresh();
      }

      EnvironmentCleaner.cleanEnvironmentFolder(distributionPackage, environmentRootFolder,
          fileManager);
    }
  }

  /**
   * Parses the configuration of a distribution package.
   *
   * @throws MojoFailureException
   *           if anything wrong happen.
   */
  private void parseConfiguration(
      final File distFolderFile,
      final List<DistributableArtifact> distributableArtifacts,
      final EnvironmentConfiguration environment, final FileManager fileManager)
      throws MojoExecutionException, MojoFailureException {

    File configFile = new File(distFolderFile, "/.eosgi.dist.xml");

    Artifact jacocoAgentArtifact = pluginArtifactMap.get("org.jacoco:org.jacoco.agent");

    LaunchConfig launchConfig = this.launchConfig.createLaunchConfigForEnvironment(
        environment.getLaunchConfig(), environment.getId(),
        reportFolder, jacocoAgentArtifact);

    checkAndAddReservedLaunchConfigurationProperties(environment, launchConfig);

    Map<String, Object> vars = new HashMap<>();
    vars.put("environmentId", environment.getId());
    vars.put("frameworkStartLevel", environment.getFrameworkStartLevel());
    vars.put("bundleStartLevel", environment.getBundleStartLevel());
    vars.put("distributableArtifacts", distributableArtifacts);
    vars.put("launchConfig", launchConfig);
    vars.put(VAR_DIST_UTIL, new DistUtil());
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
  private void parseParsables(final File distFolderFile,
      final DistributionPackageType distributionPackage, final FileManager fileManager)
      throws MojoExecutionException {

    Map<String, Object> vars = new HashMap<>();
    vars.put("distributionPackage", distributionPackage);
    vars.put(VAR_DIST_UTIL, new DistUtil());

    ParsablesType parsables = distributionPackage.getParsables();
    if (parsables != null) {

      List<ParsableType> parsable = parsables.getParsable();

      for (ParsableType p : parsable) {

        String path = p.getPath();
        File parsableFile = new File(distFolderFile, path).getAbsoluteFile();

        if (!parsableFile.exists()) {
          throw new MojoExecutionException("File that should be parsed does not exist: "
              + "[" + parsableFile.getAbsolutePath() + "]");
        }

        try {
          fileManager.replaceFileWithParsed(parsableFile, vars, p.getEncoding());
        } catch (IOException e) {
          throw new MojoExecutionException(
              "Could not replace parsable with parsed content: [" + p.getPath() + "]", e);
        }
      }
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

  private BundleDataType[] resolveBundlesToUninstall(final List<ArtifactType> bundlesToRemove) {
    List<BundleDataType> result = new ArrayList<>(bundlesToRemove.size());

    for (ArtifactType artifact : bundlesToRemove) {
      BundleDataType bundleData = artifact.getBundle();
      if (bundleData != null && !OSGiActionType.NONE.equals(bundleData.getAction())) {
        result.add(bundleData);
      }
    }

    return result.toArray(new BundleDataType[0]);
  }

  private Artifact resolveDistPackage(final String frameworkArtifact)
      throws MojoExecutionException {
    String[] distPackageIdParts;
    try {
      distPackageIdParts = resolveDistPackageId(frameworkArtifact);
    } catch (IOException e) {
      throw new MojoExecutionException("Could not get distribution package", e);
    }
    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(new DefaultArtifact(distPackageIdParts[0], distPackageIdParts[1],
        "zip", distPackageIdParts[2]));

    return artifactResolver.resolve(artifactRequest);
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

  private Artifact resolveMavenArtifactByArtifactType(final ArtifactType artifact)
      throws MojoExecutionException {

    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
        artifact.getClassifier(), artifact.getType(), artifact.getVersion()));
    return artifactResolver.resolve(artifactRequest);
  }

}
