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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.everit.osgi.dev.maven.analytics.GoogleAnalyticsTrackingService;
import org.everit.osgi.dev.maven.analytics.GoogleAnalyticsTrackingServiceImpl;
import org.everit.osgi.dev.maven.configuration.BundleSettings;
import org.everit.osgi.dev.maven.configuration.EOSGiArtifact;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.configuration.LaunchConfig;
import org.everit.osgi.dev.maven.dto.DistributableArtifact;
import org.everit.osgi.dev.maven.dto.DistributableArtifactBundleMeta;
import org.everit.osgi.dev.maven.util.PluginUtil;
import org.everit.osgi.dev.maven.util.PredefinedRepoArtifactResolver;
import org.osgi.framework.Constants;

/**
 * Mojos that extend this class can use the environment information defined for the plugin.
 */
public abstract class AbstractEOSGiMojo extends AbstractMojo {

  private static final String DEFAULT_ENVIRONMENT = "equinox";

  /**
   * The name of the referer that means who execute goal (example: eosgi-maven-plugin or
   * eclipse-e4-plugin, ...). Default value is "eosgi-maven-plugin".
   */
  @Parameter(property = "eosgi.analytics.referer", defaultValue = "eosgi-maven-plugin")
  protected String analyticsReferer;

  /**
   * The waiting time to send the analytics to Google Analytics server.
   */
  @Parameter(property = "eosgi.analytics.waiting.time", defaultValue = "3000")
  private long analyticsWaitingTimeInMs;

  @Component
  protected ArtifactHandlerManager artifactHandlerManager;

  protected PredefinedRepoArtifactResolver artifactResolver;

  /**
   * Comma separated list of the id of the environments that should be processed. Default is * that
   * means all environments.
   */
  @Parameter(name = "environmentId", property = "eosgi.environmentId", defaultValue = "*")
  protected String environmentIdsToProcess = "*";

  /**
   * The environments on which the tests should run.
   */
  @Parameter
  protected EnvironmentConfiguration[] environments;

  private EnvironmentConfiguration[] environmentsToProcess;

  @Parameter(property = "executedProject")
  protected MavenProject executedProject;

  /**
   * The configuration of the launched OSGi Container.
   */
  @Parameter
  protected LaunchConfig launchConfig;

  @Parameter(defaultValue = "${mojoExecution}", readonly = true)
  protected MojoExecution mojo;

  /**
   * The Maven project.
   */
  @Parameter(property = "project")
  protected MavenProject project;

  /**
   * The current repository/network configuration of Maven.
   */
  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  protected RepositorySystemSession repoSession;

  /**
   * The entry point to Aether, i.e. the component doing all the work.
   */
  @Component
  protected RepositorySystem repoSystem;

  @Parameter(defaultValue = "${settings}", readonly = true)
  protected Settings settings;

  /**
   * Skip analytics tracking or not. That means send event statistics to Google Analytics or not.
   * Default value is <code>false</code> that means send statistics.
   */
  @Parameter(property = "eosgi.analytics.skip", defaultValue = "false")
  private boolean skipAnalytics;

  /**
   * Appends artifacts that are defined within the environment to the distributable artifact map.
   *
   * @param environmentConfiguration
   *          The configuration of the environment.
   * @param distributableArtifacts
   *          The distributable artifacts.
   * @throws MojoExecutionException
   *           if anything happens.
   */
  protected void appendEnvironmentArtifactsToDistributables(
      final EnvironmentConfiguration environmentConfiguration,
      final Map<EOSGiArtifact, DistributableArtifact> distributableArtifacts)
      throws MojoExecutionException {

    List<EOSGiArtifact> environmentDependencies = environmentConfiguration.getDependencies();

    if (environmentDependencies == null) {
      return;
    }

    for (EOSGiArtifact eosgiArtifact : environmentDependencies) {
      org.eclipse.aether.artifact.Artifact resolvedArtifact =
          resolveArtifact(eosgiArtifact.getGroupId(), eosgiArtifact.getArtifactId(),
              eosgiArtifact.getClassifier(), eosgiArtifact.getVersion(), eosgiArtifact.getType());

      DistributableArtifact distributableArtifact = processArtifact(
          environmentConfiguration.getBundleSettings(), eosgiArtifact, resolvedArtifact.getFile());

      distributableArtifacts.put(eosgiArtifact, distributableArtifact);
    }

  }

  /**
   * Appends the artifacts of the project to the distributable artifact map.
   *
   * @param environmentConfiguration
   *          The configuration of the environment.
   * @param distributableArtifacts
   *          The distributable artifact map that will be appended.
   */
  protected void appendProjectArtifactsToDistributables(
      final EnvironmentConfiguration environmentConfiguration,
      final Map<EOSGiArtifact, DistributableArtifact> distributableArtifacts) {
    List<Artifact> availableArtifacts = new ArrayList<Artifact>(project.getArtifacts());

    if (executedProject != null) {
      availableArtifacts.add(executedProject.getArtifact());
    } else {
      availableArtifacts.add(project.getArtifact());
    }

    List<BundleSettings> bundleSettingsList = environmentConfiguration.getBundleSettings();
    for (Artifact artifact : availableArtifacts) {
      if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
        EOSGiArtifact eosgiArtifact = convertMavenToEOSGiArtifact(artifact);

        DistributableArtifact processedArtifact =
            processArtifact(bundleSettingsList, eosgiArtifact, artifact.getFile());

        distributableArtifacts.put(eosgiArtifact, processedArtifact);
      }
    }
  }

  /**
   * Converts a maven artifact to an EOSGi Artifact type.
   *
   * @param artifact
   *          The maven artifact.
   * @return The EOSGi Artifact.
   */
  protected EOSGiArtifact convertMavenToEOSGiArtifact(final Artifact artifact) {
    EOSGiArtifact result = new EOSGiArtifact();
    result.setGroupId(artifact.getGroupId());
    result.setArtifactId(artifact.getArtifactId());
    result.setType(artifact.getType());
    result.setVersion(artifact.getVersion());
    result.setClassifier(artifact.getClassifier());
    return result;
  }

  protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

  @Override
  public final void execute() throws MojoExecutionException, MojoFailureException {
    artifactResolver = new PredefinedRepoArtifactResolver(repoSystem, repoSession,
        project.getRemoteProjectRepositories(), getLog());

    MojoDescriptor mojoDescriptor = mojo.getMojoDescriptor();
    String goalName = mojoDescriptor.getGoal();

    long eventId = GoogleAnalyticsTrackingService.DEFAULT_EVENT_ID;

    String pluginVersion = this.getClass().getPackage().getImplementationVersion();
    GoogleAnalyticsTrackingService googleAnalyticsTrackingService =
        new GoogleAnalyticsTrackingServiceImpl(analyticsWaitingTimeInMs, skipAnalytics(),
            pluginVersion, getLog());

    try {
      eventId = googleAnalyticsTrackingService.sendEvent(analyticsReferer, goalName);
      doExecute();
    } finally {
      googleAnalyticsTrackingService.waitForEventSending(eventId);
    }
  }

  private BundleSettings findMatchingSettings(final List<BundleSettings> bundleSettingsList,
      final String symbolicName, final String bundleVersion) {

    for (BundleSettings settings : bundleSettingsList) {

      if (settings.getSymbolicName().equals(symbolicName)
          && ((settings.getVersion() == null) || settings.getVersion().equals(bundleVersion))) {

        return settings;
      }
    }
    return null;
  }

  /**
   * Getting the processed artifacts of the project. The artifact list is calculated each time when
   * the function is called therefore the developer should not call it inside an iteration.
   *
   * @param environmentConfiguration
   *          The configuration of the environment that the artifact list is generated for.
   * @return The list of dependencies that are OSGI bundles but do not have the scope "provided"
   * @throws MojoExecutionException
   *           if anything happens
   * @throws MalformedURLException
   *           if the URL for the artifact is broken.
   */
  protected Collection<DistributableArtifact> generateDistributableArtifacts(
      final EnvironmentConfiguration environmentConfiguration) throws MojoExecutionException {

    Map<EOSGiArtifact, DistributableArtifact> result = new LinkedHashMap<>();

    appendProjectArtifactsToDistributables(environmentConfiguration, result);

    appendEnvironmentArtifactsToDistributables(environmentConfiguration, result);

    return result.values();
  }

  /**
   * Returns the default environment. This method is called when there is no environment configured
   * for the plugin.
   *
   * @return The default environment.
   */
  protected EnvironmentConfiguration getDefaultEnvironment() {

    getLog().info("There is no environment specified in the project. "
        + "Creating " + DEFAULT_ENVIRONMENT + " environment with default settings");

    EnvironmentConfiguration defaultEnvironment = new EnvironmentConfiguration();
    defaultEnvironment.setId(DEFAULT_ENVIRONMENT);
    defaultEnvironment.setFramework(DEFAULT_ENVIRONMENT);
    return defaultEnvironment;
  }

  /**
   * Returns the environments that are configured for this plugin or the default environment
   * configuration if no environments are configured.
   *
   * @return The list of environments that are defined for the plugin.
   */
  protected EnvironmentConfiguration[] getEnvironments() {
    if ((environments == null) || (environments.length == 0)) {
      environments = new EnvironmentConfiguration[] { getDefaultEnvironment() };
    }
    return environments;
  }

  /**
   * Getting an array of the environment configurations that should be processed based on the value
   * of the {@link #environmentIdsToProcess} parameter. The value, that is returned, is calculated
   * the first time the function is called.
   *
   * @return The array of environment ids that should be processed.
   */
  protected EnvironmentConfiguration[] getEnvironmentsToProcess() {
    if (environmentsToProcess != null) {
      return environmentsToProcess;
    }

    if ("*".equals(environmentIdsToProcess)) {
      environmentsToProcess = getEnvironments();
    } else {
      String[] environmentIdArray = environmentIdsToProcess.trim().split(",");

      EnvironmentConfiguration[] tmpEnvironments = getEnvironments();

      List<EnvironmentConfiguration> result = new ArrayList<EnvironmentConfiguration>();
      for (EnvironmentConfiguration tmpEnvironment : tmpEnvironments) {
        boolean found = false;
        int j = 0;
        int n = environmentIdArray.length;
        while (!found && (j < n)) {
          if (environmentIdArray[j].equals(tmpEnvironment.getId())) {
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

  /**
   * Checking if an artifact is an OSGI bundle. An artifact is an OSGI bundle if the MANIFEST.MF
   * file inside contains a Bundle-SymbolicName.
   *
   * @param bundleSettingsList
   *          The bundle settings list of the environment that uses the artifact.
   * @param artifact
   *          The artifact that is checked.
   * @return A {@link DistributableArtifact} with the Bundle-SymbolicName and a Bundle-Version.
   *         Bundle-Version comes from MANIFEST.MF but if Bundle-Version is not available there the
   *         default 0.0.0 version is provided.
   */
  public DistributableArtifact processArtifact(final List<BundleSettings> bundleSettingsList,
      final EOSGiArtifact artifact, final File artifactFile) {

    if ("pom".equals(artifact.getType())) {
      return new DistributableArtifact(artifact, artifactFile, null, null);
    }

    if ((artifactFile == null) || !artifactFile.exists()) {
      return new DistributableArtifact(artifact, artifactFile, null, null);
    }
    Manifest manifest = null;

    try (JarFile jarFile = new JarFile(artifactFile)) {
      manifest = jarFile.getManifest();
      if (manifest == null) {
        return new DistributableArtifact(artifact, artifactFile, null, null);
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
        BundleSettings bundleSettings =
            findMatchingSettings(bundleSettingsList, symbolicName, version);
        Integer startLevel = null;
        if (bundleSettings != null) {
          startLevel = bundleSettings.getStartLevel();
        }

        bundleData =
            new DistributableArtifactBundleMeta(symbolicName, version, fragmentHost, importPackage,
                exportPackage, startLevel);
      }

      return new DistributableArtifact(artifact, artifactFile, manifest, bundleData);
    } catch (IOException e) {
      return new DistributableArtifact(artifact, artifactFile, null, null);
    }
  }

  /**
   * Resolves an artifact so its file will be available.
   *
   * @param groupId
   *          The group id of the artifact.
   * @param artifactId
   *          The artifact id of the artifact.
   * @param classifier
   *          The classifier of the artifact.
   * @param version
   *          The version of the artifact.
   * @param artifactType
   *          The type of the artifact.
   * @return The Aether resolved artifact.
   * @throws MojoExecutionException
   *           if something wrong happens.
   */
  protected org.eclipse.aether.artifact.Artifact resolveArtifact(final String groupId,
      final String artifactId,
      final String classifier, final String version, final String artifactType)
      throws MojoExecutionException {

    String tmpArtifactType = artifactType;
    if (tmpArtifactType == null) {
      tmpArtifactType = "jar";
    }
    String extension = artifactHandlerManager.getArtifactHandler(tmpArtifactType).getExtension();

    ArtifactRequest artifactRequest = new ArtifactRequest();

    artifactRequest.setArtifact(new DefaultArtifact(groupId, artifactId,
        classifier, extension, version));
    return artifactResolver.resolve(artifactRequest);
  }

  public void setEnvironmentId(final String environmentId) {
    environmentIdsToProcess = environmentId;
  }

  private boolean skipAnalytics() {
    return skipAnalytics || settings.isOffline();
  }

}
