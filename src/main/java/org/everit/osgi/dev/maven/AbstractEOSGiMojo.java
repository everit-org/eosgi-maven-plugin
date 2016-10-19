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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import org.everit.osgi.dev.dist.util.DistConstants;
import org.everit.osgi.dev.maven.analytics.GoogleAnalyticsTrackingService;
import org.everit.osgi.dev.maven.analytics.GoogleAnalyticsTrackingServiceImpl;
import org.everit.osgi.dev.maven.configuration.EOSGiArtifact;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.configuration.LaunchConfig;
import org.everit.osgi.dev.maven.util.DistributableArtifact;
import org.everit.osgi.dev.maven.util.PluginUtil;
import org.everit.osgi.dev.maven.util.PredefinedRepoArtifactResolver;
import org.osgi.framework.Constants;

/**
 * Mojos that extend this class can use the environment information defined for the plugin.
 */
public abstract class AbstractEOSGiMojo extends AbstractMojo {

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
  @Parameter(name = "environmentId", property = DistConstants.PLUGIN_PROPERTY_ENVIRONMENT_ID,
      defaultValue = "*")
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

  private EOSGiArtifact convertMavenToEOSGiArtifact(final Artifact artifact) {
    EOSGiArtifact eosgiArtifact = new EOSGiArtifact();
    StringBuilder gav =
        new StringBuilder(artifact.getGroupId()).append(":").append(artifact.getArtifactId());

    String extension = artifact.getArtifactHandler().getExtension();
    String classifier = artifact.getClassifier();
    if ("".equals(classifier)) {
      classifier = null;
    }

    if (!"jar".equals(extension) || classifier != null) {
      gav.append(':').append(extension);
    }

    if (classifier != null) {
      gav.append(':').append(classifier);
    }

    gav.append(':').append(artifact.getVersion());

    eosgiArtifact.setCoordinates(gav.toString());
    return eosgiArtifact;
  }

  /**
   * Appends the artifacts of the project to the distributable artifact map.
   *
   * @return A map where the key is the GAV and the value is the distributable artifact.
   */
  protected Map<String, DistributableArtifact> createDistributableArtifactsByGAVFromProjectDeps() {
    List<Artifact> availableArtifacts = new ArrayList<>(project.getArtifacts());

    Map<String, DistributableArtifact> distributableArtifacts = new HashMap<>();

    if (executedProject != null) {
      availableArtifacts.add(executedProject.getArtifact());
    } else {
      availableArtifacts.add(project.getArtifact());
    }

    for (Artifact artifact : availableArtifacts) {
      if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
        EOSGiArtifact eosgiArtifact = convertMavenToEOSGiArtifact(artifact);

        DistributableArtifact processedArtifact =
            processArtifact(eosgiArtifact, artifact.getFile());

        distributableArtifacts.put(processedArtifact.coordinates, processedArtifact);
      }
    }
    return distributableArtifacts;
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

  /**
   * Getting the processed artifacts of the project. The artifact list is calculated each time when
   * the function is called therefore the developer should not call it inside an iteration.
   *
   * @param environmentConfiguration
   *          The configuration of the environment that the artifact list is generated for.
   * @param projectDistributableDependencies
   *          The dependencies of the project as a GAV-DistributableArtifact map.
   * @return The list of dependencies that are OSGI bundles but do not have the scope "provided"
   * @throws MojoExecutionException
   *           if anything happens
   */
  protected Collection<DistributableArtifact> generateDistributableArtifactsForEnvironment(
      final EnvironmentConfiguration environmentConfiguration,
      final Map<String, DistributableArtifact> projectDistributableDependencies)
      throws MojoExecutionException {

    Collection<DistributableArtifact> distributableArtifacts =
        new LinkedHashSet<>(projectDistributableDependencies.values());

    List<EOSGiArtifact> environmentArtifacts = environmentConfiguration.getArtifacts();

    if (environmentArtifacts == null) {
      return distributableArtifacts;
    }

    for (EOSGiArtifact eosgiArtifact : environmentArtifacts) {
      // Remove if available in project dependencies
      DistributableArtifact projectDA =
          projectDistributableDependencies.get(eosgiArtifact.getCoordinates());

      if (projectDA != null) {
        distributableArtifacts.remove(projectDA);
      }

      // And add to the environment dependencies
      org.eclipse.aether.artifact.Artifact resolvedArtifact =
          resolveArtifact(new DefaultArtifact(eosgiArtifact.getCoordinates()));

      DistributableArtifact distributableArtifact =
          processArtifact(eosgiArtifact, resolvedArtifact.getFile());

      distributableArtifacts.add(distributableArtifact);
    }
    return distributableArtifacts;
  }

  /**
   * Returns the default environment. This method is called when there is no environment configured
   * for the plugin.
   *
   * @return The default environment.
   */
  protected EnvironmentConfiguration getDefaultEnvironment() {

    getLog().info("There is no environment specified in the project. Creating "
        + DistConstants.DEFAULT_ENVIRONMENT_ID + " environment with default settings");

    EnvironmentConfiguration defaultEnvironment = new EnvironmentConfiguration();
    defaultEnvironment.setId(DistConstants.DEFAULT_ENVIRONMENT_ID);
    defaultEnvironment.setFramework(DistConstants.DEFAULT_ENVIRONMENT_FRAMEWORK);
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

      List<EnvironmentConfiguration> result = new ArrayList<>();
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
   * Checking if an artifact is an OSGI bundle and if yes, appends its properties. An artifact is an
   * OSGI bundle if the MANIFEST.MF file inside contains a Bundle-SymbolicName.
   *
   * @param eosgiArtifact
   *          The artifact with optional additional information.
   * @param artifactFile
   *          The resolved file of the artifact.
   * @return A {@link DistributableArtifact} with the Bundle-SymbolicName and a Bundle-Version.
   *         Bundle-Version comes from MANIFEST.MF but if Bundle-Version is not available there the
   *         default 0.0.0 version is provided.
   */
  public DistributableArtifact processArtifact(
      final EOSGiArtifact eosgiArtifact, final File artifactFile) {

    DistributableArtifact distributableArtifact = new DistributableArtifact();
    distributableArtifact.coordinates = eosgiArtifact.getCoordinates();
    distributableArtifact.downloadURL = eosgiArtifact.getDownloadURL();
    distributableArtifact.targetFile = eosgiArtifact.getTargetFile();
    distributableArtifact.targetFolder = eosgiArtifact.getTargetFolder();
    distributableArtifact.properties = eosgiArtifact.getProperties();
    distributableArtifact.file = artifactFile;

    if (distributableArtifact.properties == null) {
      distributableArtifact.properties = new HashMap<>();
    }

    if ((artifactFile == null) || !artifactFile.exists()
        || !artifactFile.getName().endsWith(".jar")) {
      return distributableArtifact;
    }
    Manifest manifest = null;

    try (JarFile jarFile = new JarFile(artifactFile)) {
      manifest = jarFile.getManifest();
      if (manifest == null) {
        return distributableArtifact;
      }

      Attributes mainAttributes = manifest.getMainAttributes();
      String symbolicName = mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
      String version = mainAttributes.getValue(Constants.BUNDLE_VERSION);

      if ((symbolicName != null) && (version != null)) {
        int semicolonIndex = symbolicName.indexOf(';');
        if (semicolonIndex >= 0) {
          symbolicName = symbolicName.substring(0, semicolonIndex);
        }

        version = PluginUtil.normalizeVersion(version);

        String fragmentHost = mainAttributes.getValue(Constants.FRAGMENT_HOST);
        String importPackage = mainAttributes.getValue(Constants.IMPORT_PACKAGE);
        String exportPackage = mainAttributes.getValue(Constants.EXPORT_PACKAGE);

        Map<String, String> properties = distributableArtifact.properties;
        properties.put("bundle.symbolicName", symbolicName);
        properties.put("bundle.version", version);
        putIfNotNull(properties, "bundle.fragmentHost", fragmentHost);
        putIfNotNull(properties, "bundle.importPackage", importPackage);
        putIfNotNull(properties, "bundle.exportPackage", exportPackage);
      }

      return distributableArtifact;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void putIfNotNull(final Map<String, String> properties, final String key,
      final String value) {
    if (key != null) {
      properties.put(key, value);
    }
  }

  /**
   * Resolves an artifact so its file will be available.
   *
   * @param artifact
   *          The artifact that should be resolved.
   * @return The Aether resolved artifact.
   * @throws MojoExecutionException
   *           if something wrong happens.
   */
  protected org.eclipse.aether.artifact.Artifact resolveArtifact(
      final org.eclipse.aether.artifact.Artifact artifact)
      throws MojoExecutionException {

    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(artifact);
    return artifactResolver.resolve(artifactRequest);
  }

  public void setEnvironmentId(final String environmentId) {
    environmentIdsToProcess = environmentId;
  }

  private boolean skipAnalytics() {
    return skipAnalytics || settings.isOffline();
  }

}
