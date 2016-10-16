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
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.ReflectionException;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
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
import org.everit.osgi.dev.dist.util.DistConstants;
import org.everit.osgi.dev.dist.util.attach.EOSGiVMManager;
import org.everit.osgi.dev.dist.util.attach.EnvironmentRuntimeInfo;
import org.everit.osgi.dev.dist.util.configuration.DistributedEnvironmentConfigurationProvider;
import org.everit.osgi.dev.dist.util.configuration.schema.ArtifactType;
import org.everit.osgi.dev.dist.util.configuration.schema.ArtifactsType;
import org.everit.osgi.dev.dist.util.configuration.schema.EntryType;
import org.everit.osgi.dev.dist.util.configuration.schema.EnvironmentType;
import org.everit.osgi.dev.dist.util.configuration.schema.ParsableType;
import org.everit.osgi.dev.dist.util.configuration.schema.ParsablesType;
import org.everit.osgi.dev.dist.util.configuration.schema.PropertiesType;
import org.everit.osgi.dev.dist.util.configuration.schema.TemplateEnginesType;
import org.everit.osgi.dev.dist.util.configuration.schema.UseByType;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.configuration.LaunchConfig;
import org.everit.osgi.dev.maven.configuration.LaunchConfigOverride;
import org.everit.osgi.dev.maven.dto.DistributedEnvironmentData;
import org.everit.osgi.dev.maven.upgrade.BundleSNV;
import org.everit.osgi.dev.maven.upgrade.NoopRemoteOSGiManager;
import org.everit.osgi.dev.maven.upgrade.RemoteOSGiManager;
import org.everit.osgi.dev.maven.upgrade.RuntimeBundleInfo;
import org.everit.osgi.dev.maven.upgrade.jmx.JMXOSGiManager;
import org.everit.osgi.dev.maven.util.BundleExecutionPlan;
import org.everit.osgi.dev.maven.util.DistUtil;
import org.everit.osgi.dev.maven.util.DistributableArtifact;
import org.everit.osgi.dev.maven.util.EnvironmentCleaner;
import org.everit.osgi.dev.maven.util.FileManager;
import org.everit.osgi.dev.maven.util.PluginUtil;
import org.osgi.framework.Bundle;

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

  private static final Set<String> RESERVED_SYSPROPS;

  private static final String VAR_DIST_UTIL = "distUtil";

  static {
    RESERVED_SYSPROPS = new HashSet<>();
    RESERVED_SYSPROPS.add(DistConstants.SYSPROP_ENVIRONMENT_ID);
    RESERVED_SYSPROPS.add(DistConstants.SYSPROP_LAUNCH_UNIQUE_ID);
  }

  protected DistributedEnvironmentConfigurationProvider distEnvConfigProvider =
      new DistributedEnvironmentConfigurationProvider();

  /**
   * Path to folder where the distribution will be generated. The content of this folder will be
   * overridden if the files with same name already exist.
   *
   */
  @Parameter(property = "eosgi.distFolder", defaultValue = "${project.build.directory}/eosgi/dist")
  protected String distFolder;

  protected List<DistributedEnvironmentData> distributedEnvironmentDataCollection;

  @Parameter(defaultValue = "${localRepository}")
  protected ArtifactRepository localRepository;

  /**
   * Map of plugin artifacts.
   */
  @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
  protected Map<String, org.apache.maven.artifact.Artifact> pluginArtifactMap;

  @Parameter(defaultValue = "${session}", readonly = true)
  protected MavenSession session;

  /**
   * The directory where there may be additional files to create the distribution package
   * (optional).
   */
  @Parameter(property = "eosgi.sourceDistFolder", defaultValue = "${basedir}/src/dist/")
  protected String sourceDistFolder;

  private void checkAndAddReservedLaunchConfigurationProperties(
      final EnvironmentConfiguration environment, final LaunchConfig launchConfig)
      throws MojoFailureException {

    checkReservedSystemPropertyInVmArguments(launchConfig.getVmArguments());
    LaunchConfigOverride[] overrides = launchConfig.getOverrides();
    if (overrides != null) {
      for (LaunchConfigOverride launchConfigOverride : overrides) {
        checkReservedSystemPropertyInVmArguments(launchConfigOverride.getVmArguments());
      }
    }

    launchConfig.getVmArguments().put(DistConstants.SYSPROP_ENVIRONMENT_ID,
        "-D" + DistConstants.SYSPROP_ENVIRONMENT_ID + "=" + environment.getId());
  }

  private void checkReservedSystemPropertyInVmArguments(final Map<String, String> vmArguments)
      throws MojoFailureException {
    if (vmArguments == null) {
      return;
    }

    for (Entry<String, String> entry : vmArguments.entrySet()) {
      if (RESERVED_SYSPROPS.contains(entry.getKey())) {
        throw new MojoFailureException("'" + entry.getKey()
            + "' cannot be specified as the key of a VM argument manually"
            + " as it is a reserved word.");
      }
      String value = entry.getValue();
      if (value != null) {
        if (value.startsWith("-D")) {
          value = value.substring(2);
        }
        int indexOfEqual = value.indexOf('=');
        if (indexOfEqual >= 0) {
          value = value.substring(0, indexOfEqual);
        }
        if (RESERVED_SYSPROPS.contains(value)) {
          throw new MojoFailureException("'" + value
              + "' cannot be specified as a system property manually as it is a reserved word.");
        }
      }
    }
  }

  /**
   * On Equinox there is an exception if we do not wait 1-30 seconds with bundle update command
   * after we overcopied the file. Therefore it is better to reinstall them.
   *
   * @param bundleExecutionPlan
   *          The original execution plan that is modified.
   */
  private void convertBundleUpdatesToUninstallAndInstall(
      final BundleExecutionPlan bundleExecutionPlan) {
    for (DistributableArtifact updatableArtifact : bundleExecutionPlan.updateBundles) {
      bundleExecutionPlan.uninstallBundles.add(updatableArtifact);
      bundleExecutionPlan.installBundles.add(updatableArtifact);

      bundleExecutionPlan.changeStartLevelIfInitialBundleStartLevelChangesOnBundles
          .remove(updatableArtifact);
      bundleExecutionPlan.higherStartLevelOnBundles.remove(updatableArtifact);
      bundleExecutionPlan.lowerStartLevelOnBundles.remove(updatableArtifact);
      bundleExecutionPlan.setInitialStartLevelOnBundles.remove(updatableArtifact);
      bundleExecutionPlan.setStartLevelFromInitialBundles.remove(updatableArtifact);
      bundleExecutionPlan.startStoppedBundles.remove(updatableArtifact);
      bundleExecutionPlan.stopStartedBundles.remove(updatableArtifact);
    }
    bundleExecutionPlan.updateBundles.clear();
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

  protected EOSGiVMManager createEOSGiVMManager() {
    return new EOSGiVMManager(resolveAttachAPIClassLoader());
  }

  private Map<BundleSNV, DistributableArtifact> createJustStartedBundleByUniqueLabelMap(
      final Collection<DistributableArtifact> justStartedBundles) {
    Map<BundleSNV, DistributableArtifact> justStartedBundleByUniqueLabel = new HashMap<>();
    for (DistributableArtifact dArtifact : justStartedBundles) {
      justStartedBundleByUniqueLabel.put(toBundleSNV(dArtifact), dArtifact);
    }
    return justStartedBundleByUniqueLabel;
  }

  private RemoteOSGiManager createRemoteOSGiManager(final String environmentId,
      final File distFolderFile,
      final BundleExecutionPlan bundleExecutionPlan,
      final EnvironmentType existingDistributedEnvironment,
      final EOSGiVMManager virtualMachineManager)
      throws MojoExecutionException {

    Set<EnvironmentRuntimeInfo> runtimeInformations =
        virtualMachineManager.getRuntimeInformations(environmentId, distFolderFile);

    if (runtimeInformations.isEmpty()) {
      return new NoopRemoteOSGiManager();
    }

    if (runtimeInformations.size() > 1) {

      StringBuilder sb = new StringBuilder();
      for (EnvironmentRuntimeInfo environmentRuntimeInfo : runtimeInformations) {
        if (sb.length() > 0) {
          sb.append(',');
        }
        sb.append(environmentRuntimeInfo.virtualMachineId);
      }

      throw new MojoExecutionException(
          "Found multiple running JVMs that belong to the environment."
              + " Live upgrade is supported only on one running JVM:'" + environmentId
              + "', virtualMachines = '" + sb.toString() + "'");
    }

    EnvironmentRuntimeInfo runtimeInfo = runtimeInformations.iterator().next();

    if (existingDistributedEnvironment == null) {
      throw new MojoExecutionException(
          "Found JVM that belongs to Environment, but there is no distributed environment in the"
              + " file system. Try stopping the Environment JVM before runnign the distribution"
              + " upgrade: [environmentId = '" + environmentId + "', virtualMachines = '"
              + runtimeInfo.virtualMachineId + "'");
    }

    if (isBundleExecutionPlanEmpty(bundleExecutionPlan)) {
      return new NoopRemoteOSGiManager();
    }

    String jmxServiceURL = runtimeInfo.jmxServiceURL;

    if (jmxServiceURL == null) {
      throw new MojoExecutionException("Bundle changes cannot be applied on the running OSGi"
          + " container as JMX service URL is not available: [environmentId = '" + environmentId
          + "', virtualMachines = '" + runtimeInfo.virtualMachineId + "'");
    }

    try {
      return new JMXOSGiManager(jmxServiceURL, getLog());
    } catch (IOException | InstanceNotFoundException | IntrospectionException
        | ReflectionException e) {

      throw new MojoExecutionException(
          "Could not connect to JVM that belongs to environment:  [environmentId = '"
              + environmentId + "', virtualMachines = '" + runtimeInfo.virtualMachineId + "'",
          e);
    }

  }

  private Map<BundleSNV, DistributableArtifact> createStartActionBundleByUniqueLabelMap(
      final Collection<DistributableArtifact> distributedArtifacts) {
    Map<BundleSNV, DistributableArtifact> shouldBeActiveBundleByUniqueLabel = new HashMap<>();
    for (DistributableArtifact artifact : distributedArtifacts) {
      if (EOSGiConstants.BUNDLE_ACTION_START.equals(resolveBundleAction(artifact))) {
        shouldBeActiveBundleByUniqueLabel.put(toBundleSNV(artifact), artifact);
      }
    }
    return shouldBeActiveBundleByUniqueLabel;
  }

  private void distributeArtifactFiles(final File envDistFolderFile,
      final Collection<DistributableArtifact> dArtifacts, final FileManager fileManager)
      throws MojoExecutionException {

    for (DistributableArtifact dArtifact : dArtifacts) {

      Artifact mavenArtifact = resolveMavenArtifactByArtifactType(dArtifact);

      File targetFile =
          PluginUtil.resolveArtifactAbsoluteFile(dArtifact, mavenArtifact, envDistFolderFile);

      fileManager.overCopyFile(mavenArtifact.getFile(), targetFile);
    }
  }

  @Override
  protected void doExecute() throws MojoExecutionException, MojoFailureException {

    try (EOSGiVMManager virtualMachineManager = createEOSGiVMManager()) {

      File globalDistFolderFile = new File(distFolder);

      distributedEnvironmentDataCollection = new ArrayList<>();
      EnvironmentConfiguration[] environmentsToProcess = getEnvironmentsToProcess();

      Map<String, DistributableArtifact> projectDistributableDependencies =
          createDistributableArtifactsByGAVFromProjectDeps();

      for (EnvironmentConfiguration environment : environmentsToProcess) {
        executeOnEnvironment(globalDistFolderFile, environment, projectDistributableDependencies,
            virtualMachineManager);
      }
    }
  }

  private void executeOnEnvironment(final File globalDistFolderFile,
      final EnvironmentConfiguration environment,
      final Map<String, DistributableArtifact> projectDistributableDependencies,
      final EOSGiVMManager virtualMachineManager)
      throws MojoExecutionException, MojoFailureException {

    FileManager fileManager = new FileManager();

    Collection<DistributableArtifact> distributableArtifacts =
        generateDistributableArtifactsForEnvironment(
            environment, projectDistributableDependencies);

    String environmentId = environment.getId();

    Artifact distPackageArtifact = resolveDistPackage(environment.getFramework());
    File distPackageFile = distPackageArtifact.getFile();
    File environmentRootFolder = new File(globalDistFolderFile, environmentId);

    File environmentConfigurationFile =
        new File(environmentRootFolder, DistConstants.FILE_NAME_EOSGI_DIST_CONFIG);

    EnvironmentType existingDistributedEnvironment = distEnvConfigProvider
        .getOverriddenDistributedEnvironmentConfig(environmentConfigurationFile,
            UseByType.PARSABLES);

    Collection<DistributableArtifact> existingDistributedArtifacts = processArtifacts(
        existingDistributedEnvironment);

    File environmentConfigurationTempFile =
        unpackDistConfigFileToNewTempFile(distPackageFile, fileManager);

    processConfigurationTemplate(environmentConfigurationTempFile, distributableArtifacts,
        environment, fileManager);

    EnvironmentType distributedEnvironment = distEnvConfigProvider
        .getOverriddenDistributedEnvironmentConfig(environmentConfigurationTempFile,
            UseByType.PARSABLES);

    fileManager.unpackZipFile(distPackageFile, environmentRootFolder,
        DistConstants.FILE_NAME_EOSGI_DIST_CONFIG);
    copyDistFolderToTargetIfExists(environmentRootFolder, fileManager);

    Collection<DistributableArtifact> newDistributedArtifacts =
        processArtifacts(distributedEnvironment);

    BundleExecutionPlan bundleExecutionPlan = new BundleExecutionPlan(environment.getId(),
        existingDistributedArtifacts, newDistributedArtifacts, environmentRootFolder,
        artifactResolver);

    fileManager.overCopyFile(environmentConfigurationTempFile, environmentConfigurationFile);
    environmentConfigurationTempFile.delete();

    convertBundleUpdatesToUninstallAndInstall(bundleExecutionPlan);

    try (RemoteOSGiManager remoteOSGiManager =
        createRemoteOSGiManager(environmentId, environmentRootFolder,
            bundleExecutionPlan, existingDistributedEnvironment, virtualMachineManager)) {

      int originalFrameworkStartLevel = remoteOSGiManager.getFrameworkStartLevel();

      int frameworkStartLevelAfterUpgrade = resolveFrameworkStartLevelAfterUpgrade(
          distributedEnvironment.getFrameworkStartLevel(), originalFrameworkStartLevel);

      int currentInitialBundleStartLevel = remoteOSGiManager.getInitialBundleStartLevel();
      int newInitialBundleStartLevel = (distributedEnvironment.getInitialBundleStartLevel() != null)
          ? distributedEnvironment.getInitialBundleStartLevel() : currentInitialBundleStartLevel;

      int frameworkStartLevelDuringUpdate = resolveNecessaryStartlevel(bundleExecutionPlan,
          originalFrameworkStartLevel, currentInitialBundleStartLevel);

      try {
        if (frameworkStartLevelDuringUpdate < originalFrameworkStartLevel) {
          remoteOSGiManager.setFrameworkStartLevel(frameworkStartLevelDuringUpdate);
        }

        remoteOSGiManager.stopBundles(toBundleSNVs(bundleExecutionPlan.updateBundles));

        remoteOSGiManager.stopBundles(toBundleSNVs(bundleExecutionPlan.stopStartedBundles));

        higherBundleStartLevelWhereNecessary(bundleExecutionPlan, currentInitialBundleStartLevel,
            newInitialBundleStartLevel, remoteOSGiManager);

        remoteOSGiManager.uninstallBundles(toBundleSNVs(bundleExecutionPlan.uninstallBundles));

        if (newInitialBundleStartLevel != currentInitialBundleStartLevel) {
          remoteOSGiManager.setInitialBundleStartLevel(newInitialBundleStartLevel);
        }

        distributeArtifactFiles(environmentRootFolder, newDistributedArtifacts, fileManager);

        remoteOSGiManager
            .installBundles(toBundleSNVLocationMap(bundleExecutionPlan.installBundles));

        setStartLevelOnNewlyInstalledBundles(bundleExecutionPlan.installBundles, remoteOSGiManager);

        remoteOSGiManager.updateBundles(toBundleSNVs(bundleExecutionPlan.updateBundles));

        remoteOSGiManager.resolveAll();
        remoteOSGiManager.refresh();

        lowerBundleStartLevelWhereNecessary(bundleExecutionPlan, currentInitialBundleStartLevel,
            newInitialBundleStartLevel, remoteOSGiManager);

        parseParsables(environmentRootFolder, distributedEnvironment, newDistributedArtifacts,
            fileManager);

        startBundlesWhereNecessary(bundleExecutionPlan, newDistributedArtifacts, remoteOSGiManager);

        distributedEnvironmentDataCollection.add(new DistributedEnvironmentData(environment,
            distributedEnvironment, environmentRootFolder, newDistributedArtifacts));

        EnvironmentCleaner.cleanEnvironmentFolder(distributedEnvironment, environmentRootFolder,
            fileManager);
      } finally {
        if (frameworkStartLevelAfterUpgrade != frameworkStartLevelDuringUpdate) {
          remoteOSGiManager.setFrameworkStartLevel(frameworkStartLevelAfterUpgrade);
        }
      }

    }
  }

  private void higherBundleStartLevelWhereNecessary(final BundleExecutionPlan bundleExecutionPlan,
      final int currentInitialBundleStartLevel, final int newInitialBundleStartLevel,
      final RemoteOSGiManager remoteOSGiManager) {

    if (newInitialBundleStartLevel > currentInitialBundleStartLevel) {
      for (DistributableArtifact dArtifact : bundleExecutionPlan.changeStartLevelIfInitialBundleStartLevelChangesOnBundles) { // CS_DISABLE_LINE_LENGTH
        remoteOSGiManager.setBundleStartLevel(toBundleSNV(dArtifact),
            newInitialBundleStartLevel);
      }
    }

    for (DistributableArtifact dArtifact : bundleExecutionPlan.higherStartLevelOnBundles) {
      remoteOSGiManager.setBundleStartLevel(toBundleSNV(dArtifact),
          resolveStartLevel(dArtifact));
    }

    for (Entry<DistributableArtifact, Integer> artifactWithOldSL : bundleExecutionPlan.setInitialStartLevelOnBundles // CS_DISABLE_LINE_LENGTH
        .entrySet()) {

      int oldStartLevel = artifactWithOldSL.getValue();
      if (oldStartLevel < newInitialBundleStartLevel) {
        remoteOSGiManager.setBundleStartLevel(toBundleSNV(artifactWithOldSL.getKey()),
            newInitialBundleStartLevel);
      }
    }

    for (DistributableArtifact dArtifact : bundleExecutionPlan.setStartLevelFromInitialBundles) {

      Integer startLevel = resolveStartLevel(dArtifact);
      if (startLevel.compareTo(newInitialBundleStartLevel) > 0) {
        remoteOSGiManager.setBundleStartLevel(toBundleSNV(dArtifact), startLevel);
      }
    }
  }

  private boolean isBundleExecutionPlanEmpty(final BundleExecutionPlan bundleExecutionPlan) {
    return bundleExecutionPlan.installBundles.size() == 0
        && bundleExecutionPlan.uninstallBundles.size() == 0
        && bundleExecutionPlan.updateBundles.size() == 0
        && bundleExecutionPlan.changeStartLevelIfInitialBundleStartLevelChangesOnBundles.size() != 0
        && bundleExecutionPlan.higherStartLevelOnBundles.size() != 0
        && bundleExecutionPlan.lowerStartLevelOnBundles.size() != 0
        && bundleExecutionPlan.setInitialStartLevelOnBundles.size() != 0
        && bundleExecutionPlan.setStartLevelFromInitialBundles.size() != 0
        && bundleExecutionPlan.startStoppedBundles.size() != 0
        && bundleExecutionPlan.stopStartedBundles.size() != 0;
  }

  private void lowerBundleStartLevelWhereNecessary(final BundleExecutionPlan bundleExecutionPlan,
      final int currentInitialBundleStartLevel, final int newInitialBundleStartLevel,
      final RemoteOSGiManager remoteOSGiManager) {

    if (newInitialBundleStartLevel < currentInitialBundleStartLevel) {
      for (DistributableArtifact dArtifact : bundleExecutionPlan.changeStartLevelIfInitialBundleStartLevelChangesOnBundles) { // CS_DISABLE_LINE_LENGTH
        remoteOSGiManager.setBundleStartLevel(toBundleSNV(dArtifact),
            newInitialBundleStartLevel);
      }
    }

    for (DistributableArtifact dArtifact : bundleExecutionPlan.lowerStartLevelOnBundles) {
      remoteOSGiManager.setBundleStartLevel(toBundleSNV(dArtifact),
          resolveStartLevel(dArtifact));
    }

    for (Entry<DistributableArtifact, Integer> dArtifactWithOldSL : bundleExecutionPlan.setInitialStartLevelOnBundles // CS_DISABLE_LINE_LENGTH
        .entrySet()) {
      int oldStartLevel = dArtifactWithOldSL.getValue();
      if (oldStartLevel > newInitialBundleStartLevel) {
        remoteOSGiManager.setBundleStartLevel(toBundleSNV(dArtifactWithOldSL.getKey()),
            newInitialBundleStartLevel);
      }
    }

    for (DistributableArtifact dArtifact : bundleExecutionPlan.setStartLevelFromInitialBundles) {

      Integer startLevel = resolveStartLevel(dArtifact);
      if (startLevel.compareTo(newInitialBundleStartLevel) < 0) {
        remoteOSGiManager.setBundleStartLevel(toBundleSNV(dArtifact), startLevel);
      }
    }
  }

  /**
   * Parses and processes the files that are templates.
   *
   * @param distributedArtifacts
   *          The artifacts that are newly distributed to the system.
   */
  private void parseParsables(final File distFolderFile,
      final EnvironmentType distributedEnvironment,
      final Collection<DistributableArtifact> distributedArtifacts,
      final FileManager fileManager) throws MojoExecutionException {

    Map<String, Object> vars = new HashMap<>();
    vars.put("distributedEnvironment", distributedEnvironment);
    vars.put("distributedArtifacts", distributedArtifacts);
    vars.put(VAR_DIST_UTIL, new DistUtil());

    ParsablesType parsables = distributedEnvironment.getParsables();
    if (parsables != null) {

      List<ParsableType> parsable = parsables.getParsable();

      for (ParsableType p : parsable) {

        String path = p.getPath();
        File parsableFile = new File(distFolderFile, path).getAbsoluteFile();

        if (!parsableFile.exists()) {
          throw new MojoExecutionException(
              "File that should be parsed does not exist: " + "[" + parsableFile.getAbsolutePath()
                  + "]");
        }

        try {
          String encoding = p.getEncoding();
          if (encoding == null) {
            encoding = "UTF8";
          }
          fileManager.replaceFileWithParsed(parsableFile, vars, encoding, p.getTemplateEngine(),
              false);
        } catch (IOException e) {
          throw new MojoExecutionException(
              "Could not replace parsable with parsed content: [" + p.getPath() + "]", e);
        }
      }
    }
  }

  private Collection<DistributableArtifact> processArtifacts(final EnvironmentType environment) {

    List<DistributableArtifact> result = new ArrayList<>();

    if (environment == null) {
      return result;
    }

    ArtifactsType artifacts = environment.getArtifacts();
    if (artifacts == null) {
      return result;
    }

    List<ArtifactType> artifactList = artifacts.getArtifact();
    for (ArtifactType artifact : artifactList) {
      DistributableArtifact distributableArtifact = new DistributableArtifact();
      distributableArtifact.targetFile = artifact.getTargetFile();
      distributableArtifact.targetFolder = artifact.getTargetFolder();
      distributableArtifact.coordinates = artifact.getCoordinates();

      // TODO resolve file and downloadURL if possible

      PropertiesType properties = artifact.getProperties();
      if (properties != null) {
        for (EntryType entry : properties.getProperty()) {
          distributableArtifact.properties.put(entry.getKey(), entry.getValue());
        }
      }
      result.add(distributableArtifact);
    }
    return result;
  }

  /**
   * Parses the configuration of a distribution package.
   *
   * @throws MojoFailureException
   *           if anything wrong happen.
   */
  private void processConfigurationTemplate(final File configFile,
      final Collection<DistributableArtifact> distributableArtifacts,
      final EnvironmentConfiguration environment, final FileManager fileManager)
      throws MojoExecutionException, MojoFailureException {

    LaunchConfig launchConfig = environment.getLaunchConfig();
    if (this.launchConfig != null) {
      launchConfig =
          this.launchConfig.createLaunchConfigForEnvironment(launchConfig);
    }

    if (launchConfig == null) {
      launchConfig = new LaunchConfig();
    }

    checkAndAddReservedLaunchConfigurationProperties(environment, launchConfig);

    Map<String, Object> vars = new HashMap<>();
    vars.put("environmentId", environment.getId());
    vars.put("frameworkStartLevel", environment.getFrameworkStartLevel());
    vars.put("bundleStartLevel", environment.getInitialBundleStartLevel());
    vars.put("distributableArtifacts", distributableArtifacts);
    vars.put("runtimePathRegexes", environment.getRuntimePathRegexes());
    vars.put("launchConfig", launchConfig);
    vars.put(VAR_DIST_UTIL, new DistUtil());
    try {
      fileManager.replaceFileWithParsed(configFile, vars, "UTF8", TemplateEnginesType.XML, true);
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Could not run velocity on configuration file: " + configFile.getName(),
          e);
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
    Enumeration<URL> resources = this.getClass().getClassLoader()
        .getResources("META-INF/eosgi-frameworks.properties");
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

  private ClassLoader resolveAttachAPIClassLoader() {
    Object classLoaderObj = session.getRequest().getData()
        .get(DistConstants.MAVEN_EXECUTION_REQUEST_DATA_KEY_ATTACH_API_CLASSLOADER);

    if (classLoaderObj != null) {
      return (ClassLoader) classLoaderObj;
    }
    return DistMojo.class.getClassLoader();
  }

  private String resolveBundleAction(final DistributableArtifact dArtifact) {

    String bundleActionString =
        dArtifact.properties.get(EOSGiConstants.ARTIFACT_PROPERTY_BUNDLE_ACTION);

    if (bundleActionString == null) {
      return null;
    }
    return bundleActionString.toLowerCase();
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
    artifactRequest.setArtifact(
        new DefaultArtifact(distPackageIdParts[0], distPackageIdParts[1], "zip",
            distPackageIdParts[2]));

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
  private String[] resolveDistPackageId(final String pFrameworkArtifact)
      throws IOException, MojoExecutionException {

    String frameworkArtifact = (pFrameworkArtifact != null) ? pFrameworkArtifact
        : DistConstants.DEFAULT_ENVIRONMENT_FRAMEWORK;

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
        getLog().info("Dist package definition '" + frameworkArtifact + "'  was resolved to be '"
            + defaultFrameworkDistPackage + "'");
      }
    }
    if (distPackageParts.length != MAVEN_ARTIFACT_ID_PART_NUM) {
      throw new MojoExecutionException(
          "Invalid distribution package id format: " + frameworkArtifact);
    }
    return distPackageParts;
  }

  private int resolveFrameworkStartLevelAfterUpgrade(final Integer frameworkStartLevel,
      final int originalFrameworkStartLevel) {

    return (frameworkStartLevel != null) ? frameworkStartLevel : originalFrameworkStartLevel;
  }

  private Set<DistributableArtifact> resolveJustStartedActiveBundles(
      final RemoteOSGiManager remoteOSGiManager,
      final Map<BundleSNV, DistributableArtifact> justStartedBundleByUniqueLabel) {
    RuntimeBundleInfo[] runtimeBundleInfoArray = remoteOSGiManager.getRuntimeBundleInfoArray();
    Set<DistributableArtifact> activeJustStartedBundleSet = new HashSet<>();
    for (RuntimeBundleInfo runtimeBundleInfo : runtimeBundleInfoArray) {
      DistributableArtifact bundleData =
          justStartedBundleByUniqueLabel.get(toBundleSNV(runtimeBundleInfo));
      if (runtimeBundleInfo.state == Bundle.ACTIVE && bundleData != null) {
        activeJustStartedBundleSet.add(bundleData);
      }
    }
    return activeJustStartedBundleSet;
  }

  private Artifact resolveMavenArtifactByArtifactType(final DistributableArtifact artifact)
      throws MojoExecutionException {

    return resolveArtifact(new DefaultArtifact(artifact.coordinates, artifact.properties));
  }

  private int resolveNecessaryStartlevel(final BundleExecutionPlan bundleExecutionPlan,
      final int originalFrameworkStartLevel, final int initialBundleStartLevel) {

    int newStartLevel = originalFrameworkStartLevel;

    Integer lowestBundleStartLevel = bundleExecutionPlan.lowestStartLevel;
    if (lowestBundleStartLevel != null && lowestBundleStartLevel.compareTo(newStartLevel) < 0) {
      newStartLevel = lowestBundleStartLevel;
    }

    if (initialBundleStartLevel < newStartLevel
        && bundleExecutionPlan.changeStartLevelIfInitialBundleStartLevelChangesOnBundles.size() > 0
        && bundleExecutionPlan.installBundles.size() > 0
        && bundleExecutionPlan.setInitialStartLevelOnBundles.size() > 0
        && bundleExecutionPlan.setStartLevelFromInitialBundles.size() > 0) {
      newStartLevel = initialBundleStartLevel;
    }

    return newStartLevel;
  }

  private List<DistributableArtifact> resolveResolvedBundlesInJustStartedActiveBundleDependencyClosure(// CS_DISABLE_LINE_LENGTH
      final Map<BundleSNV, DistributableArtifact> shouldBeActiveBundleByUniqueLabel,
      final RuntimeBundleInfo[] dependencyClosure) {
    List<DistributableArtifact> bundlesInClosureToStart = new ArrayList<>();
    for (RuntimeBundleInfo runtimeBundleInfo : dependencyClosure) {
      if (runtimeBundleInfo.state == Bundle.RESOLVED) {
        DistributableArtifact bundleData =
            shouldBeActiveBundleByUniqueLabel.get(toBundleSNV(runtimeBundleInfo));
        if (bundleData != null) {
          bundlesInClosureToStart.add(bundleData);
        }
      }
    }
    return bundlesInClosureToStart;
  }

  private Integer resolveStartLevel(final DistributableArtifact distributableArtifact) {
    String startLevelString = distributableArtifact.properties.get("bundle.startLevel");
    if (startLevelString == null) {
      return null;
    }
    return Integer.parseInt(startLevelString);
  }

  private void setStartLevelOnNewlyInstalledBundles(
      final Collection<DistributableArtifact> installBundles,
      final RemoteOSGiManager remoteOSGiManager) {
    for (DistributableArtifact distributableArtifact : installBundles) {
      Integer startLevelString = resolveStartLevel(distributableArtifact);
      if (startLevelString != null) {
        remoteOSGiManager.setBundleStartLevel(toBundleSNV(distributableArtifact),
            startLevelString);
      }
    }
  }

  private void startBundlesWhereNecessary(final BundleExecutionPlan bundleExecutionPlan,
      final Collection<DistributableArtifact> distributedArtifacts,
      final RemoteOSGiManager remoteOSGiManager) {

    Set<DistributableArtifact> bundlesToStart =
        new LinkedHashSet<>(bundleExecutionPlan.startStoppedBundles);

    for (DistributableArtifact dArtifact : bundleExecutionPlan.updateBundles) {
      if (EOSGiConstants.BUNDLE_ACTION_START.equals(resolveBundleAction(dArtifact))) {
        bundlesToStart.add(dArtifact);
      }
    }

    for (DistributableArtifact dArtifact : bundleExecutionPlan.installBundles) {
      if (EOSGiConstants.BUNDLE_ACTION_START.equals(resolveBundleAction(dArtifact))) {
        bundlesToStart.add(dArtifact);
      }
    }

    remoteOSGiManager.startBundles(toBundleSNVs(bundlesToStart));

    startResolvedBundlesOfJustStartedActiveBundlesDependencies(bundlesToStart, distributedArtifacts,
        remoteOSGiManager);
  }

  private void startResolvedBundlesOfJustStartedActiveBundlesDependencies(
      final Collection<DistributableArtifact> justStartedBundles,
      final Collection<DistributableArtifact> distributedArtifacts,
      final RemoteOSGiManager remoteOSGiManager) {

    Map<BundleSNV, DistributableArtifact> justStartedBundleByUniqueLabel =
        createJustStartedBundleByUniqueLabelMap(justStartedBundles);

    Set<DistributableArtifact> activeJustStartedBundleSet =
        resolveJustStartedActiveBundles(remoteOSGiManager, justStartedBundleByUniqueLabel);

    Map<BundleSNV, DistributableArtifact> shouldBeActiveBundleByUniqueLabel =
        createStartActionBundleByUniqueLabelMap(
            distributedArtifacts);

    RuntimeBundleInfo[] dependencyClosure = remoteOSGiManager
        .getDependencyClosure(toBundleSNVs(activeJustStartedBundleSet));

    List<DistributableArtifact> bundlesInClosureToStart =
        resolveResolvedBundlesInJustStartedActiveBundleDependencyClosure(
            shouldBeActiveBundleByUniqueLabel, dependencyClosure);

    remoteOSGiManager.startBundles(toBundleSNVs(bundlesInClosureToStart));
  }

  private BundleSNV toBundleSNV(final DistributableArtifact distributableArtifact) {
    String symbolicName = distributableArtifact.properties.get("bundle.symbolicName");
    String version = distributableArtifact.properties.get("bundle.version");

    return new BundleSNV(symbolicName, version);
  }

  private Object toBundleSNV(final RuntimeBundleInfo runtimeBundleInfo) {
    return new BundleSNV(runtimeBundleInfo.symbolicName, runtimeBundleInfo.version);
  }

  private Map<BundleSNV, String> toBundleSNVLocationMap(
      final Collection<DistributableArtifact> installBundles) {
    Map<BundleSNV, String> result = new LinkedHashMap<>();
    for (DistributableArtifact distributableArtifact : installBundles) {
      BundleSNV bundleSNV = toBundleSNV(distributableArtifact);
      String location = distributableArtifact.properties.get("bundle.location");
      result.put(bundleSNV, location);
    }
    return result;
  }

  private Collection<BundleSNV> toBundleSNVs(
      final Collection<DistributableArtifact> distributableArtifacts) {

    List<BundleSNV> result = new ArrayList<>(distributableArtifacts.size());

    for (DistributableArtifact distributableArtifact : distributableArtifacts) {
      result.add(toBundleSNV(distributableArtifact));
    }

    return result;
  }

  private File unpackDistConfigFileToNewTempFile(final File distPackageFile,
      final FileManager fileManager) {
    try {
      File environmentConfigurationTempFile =
          File.createTempFile("eosgi", DistConstants.FILE_NAME_EOSGI_DIST_CONFIG);

      fileManager.unpackZipEntry(distPackageFile, environmentConfigurationTempFile,
          DistConstants.FILE_NAME_EOSGI_DIST_CONFIG);

      return environmentConfigurationTempFile;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

  }

}
