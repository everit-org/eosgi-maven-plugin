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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.ReflectionException;

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
import org.everit.osgi.dev.eosgi.dist.schema.util.EnvironmentConfigurationDTO;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactsType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.DistributionPackageType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ParsableType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ParsablesType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.UseByType;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.configuration.LaunchConfig;
import org.everit.osgi.dev.maven.dto.DistributableArtifact;
import org.everit.osgi.dev.maven.dto.DistributedEnvironment;
import org.everit.osgi.dev.maven.upgrade.RemoteOSGiManager;
import org.everit.osgi.dev.maven.upgrade.jmx.JMXOSGiManager;
import org.everit.osgi.dev.maven.util.DistUtil;
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

  private static final String DCOM_SUN_MANAGEMENT_JMXREMOTE_PORT =
      "-Dcom.sun.management.jmxremote.port=";

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

  private RemoteOSGiManager createRemoteOSGiManager(
      final EnvironmentConfigurationDTO environmentConfigurationDTO) {

    Integer port = getRemoteUpgradePort(environmentConfigurationDTO);
    if (port == null) {
      return null;
    }

    try {
      return new JMXOSGiManager(port, getLog());
    } catch (IOException | InstanceNotFoundException | IntrospectionException
        | ReflectionException e) {
      getLog().info("Incremental OSGi bundle update is not available. "
          + "Caused by: " + e.getClass().getName() + " " + e.getMessage());
      getLog().debug(e);
    }

    return null;
  }

  private void distributeArtifacts(final RemoteOSGiManager remoteOSGiManager,
      final File envDistFolderFile, final Map<String, ArtifactType> existingArtifactMap,
      final ArtifactsType artifactsType)
          throws MojoExecutionException {

    if (artifactsType == null) {
      return;
    }

    List<ArtifactType> artifacts = artifactsType.getArtifact();

    List<BundleDataType> bundlesToUpdate = new ArrayList<>();
    List<BundleDataType> bundlesToInstall = new ArrayList<>();

    for (ArtifactType artifact : artifacts) {

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
  }

  @Override
  protected void doExecute() throws MojoExecutionException, MojoFailureException {

    fileManager = new FileManager();
    File globalDistFolderFile = new File(distFolder);

    distributedEnvironments = new ArrayList<DistributedEnvironment>();
    EnvironmentConfiguration[] environmentsToProcess = getEnvironmentsToProcess();

    for (EnvironmentConfiguration environment : environmentsToProcess) {
      executeOnEnvironment(globalDistFolderFile, environment);
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

  private void executeOnEnvironment(final File globalDistFolderFile,
      final EnvironmentConfiguration environment)
          throws MojoExecutionException {

    List<DistributableArtifact> processedArtifacts;
    try {
      processedArtifacts = generateDistributableArtifacts(environment.getBundleSettings());
    } catch (MalformedURLException e) {
      throw new MojoExecutionException("Could not resolve dependent artifacts of project", e);
    }

    Artifact distPackageArtifact = resolveDistPackage(environment.getFramework());
    File distPackageFile = distPackageArtifact.getFile();
    File distFolderFile = new File(globalDistFolderFile, environment.getId());

    DistributionPackageType existingDistributionPackage =
        distSchemaProvider.getOverridedDistributionPackage(distFolderFile, UseByType.PARSABLES);
    EnvironmentConfigurationDTO existingEnvConfig = distSchemaProvider.getEnvironmentConfiguration(
        existingDistributionPackage);

    RemoteOSGiManager remoteOSGiManager = null;

    try (ZipFile distPackageZipFile = new ZipFile(distPackageFile)) {

      fileManager.unpackZipFile(distPackageFile, distFolderFile);

      if (sourceDistPath != null) {
        File sourceDistPathFile = new File(sourceDistPath);
        if (sourceDistPathFile.exists() && sourceDistPathFile.isDirectory()) {
          fileManager.copyDirectory(sourceDistPathFile, distFolderFile);
        }
      }

      parseConfiguration(distFolderFile, processedArtifacts, environment);

      DistributionPackageType distributionPackage =
          distSchemaProvider.getOverridedDistributionPackage(distFolderFile, UseByType.PARSABLES);
      EnvironmentConfigurationDTO envConfig = distSchemaProvider.getEnvironmentConfiguration(
          distributionPackage);

      ArtifactsType artifacts = distributionPackage.getArtifacts();

      Map<String, ArtifactType> existingArtifactMap =
          PluginUtil.createArtifactMap(existingDistributionPackage);
      List<ArtifactType> artifactsToRemove =
          PluginUtil.getArtifactsToRemove(existingArtifactMap, artifacts);

      if (existingDistributionPackage != null) {
        remoteOSGiManager = createRemoteOSGiManager(envConfig);
      }

      removeArtifactsFromDistributedEnvironment(
          remoteOSGiManager, distFolderFile, artifactsToRemove);

      distributeArtifacts(
          remoteOSGiManager, distFolderFile, existingArtifactMap, artifacts);

      parseParsables(distFolderFile, distributionPackage);
      distributedEnvironments.add(
          new DistributedEnvironment(environment, distributionPackage,
              distFolderFile, processedArtifacts));

      if (envConfig.isChanged(existingEnvConfig)) {
        getLog().warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        getLog().warn("!!! The environment configuration has been changed. "
            + "[" + environment.getId() + "] should be restarted.");
        getLog().warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      }

      if (remoteOSGiManager != null) {
        remoteOSGiManager.refresh();
      }

    } catch (IOException e) {

      throw new MojoExecutionException("Could not uncompress distribution package file: "
          + distPackageFile.toString(), e);

    } finally {

      if (remoteOSGiManager != null) {
        remoteOSGiManager.disconnect();
      }

    }
  }

  private Integer getRemoteUpgradePort(
      final EnvironmentConfigurationDTO environmentConfigurationDTO) {

    for (String vmArgument : environmentConfigurationDTO.vmArguments) {
      if (vmArgument.startsWith(DCOM_SUN_MANAGEMENT_JMXREMOTE_PORT)) {
        return Integer.valueOf(vmArgument.substring(DCOM_SUN_MANAGEMENT_JMXREMOTE_PORT.length()));
      }
    }

    getLog().warn(
        "JMX remote port is not defined in <vmArguments> section of PARSABLES <useByType>. "
            + "Incremental OSGi bundle updates cannot be performed.");

    return null;
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

    Artifact jacocoAgentArtifact = pluginArtifactMap.get("org.jacoco:org.jacoco.agent");

    LaunchConfig launchConfig = this.launchConfig.createLaunchConfigForEnvironment(
        environment.getLaunchConfig(), environment.getId(),
        reportFolder, jacocoAgentArtifact);

    Map<String, Object> vars = new HashMap<>();
    vars.put("environmentId", environment.getId());
    vars.put("frameworkStartLevel", environment.getFrameworkStartLevel());
    vars.put("bundleStartLevel", environment.getBundleStartLevel());
    vars.put("distributableArtifacts", distributableArtifacts);
    vars.put("launchConfig", launchConfig);
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
  private void parseParsables(final File distFolderFile,
      final DistributionPackageType distributionPackage)
          throws MojoExecutionException {

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

  private void removeArtifactsFromDistributedEnvironment(final RemoteOSGiManager remoteOSGiManager,
      final File distFolderFile, final List<ArtifactType> artifactsToRemove) {

    if (remoteOSGiManager != null) {
      remoteOSGiManager.uninstallBundles(toBundles(artifactsToRemove));
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
      if (!artifactFile.delete()) {
        getLog().warn("Failed to remove artifact [" + artifactFile.getAbsolutePath()
            + "] from the file system. Incremental updating will not work properly.");
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

  private BundleDataType[] toBundles(final List<ArtifactType> artifactTypes) {

    if (artifactTypes == null) {
      return new BundleDataType[] {};
    }

    List<BundleDataType> bundleDataTypes = new ArrayList<>();
    for (ArtifactType artifactType : artifactTypes) {

      BundleDataType bundleDataType = artifactType.getBundle();

      if (bundleDataType != null) {
        bundleDataTypes.add(bundleDataType);
      }
    }

    return bundleDataTypes.toArray(new BundleDataType[] {});
  }

}
