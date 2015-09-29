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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.everit.osgi.dev.maven.util.PluginUtil;
import org.osgi.framework.Constants;

public abstract class AbstractEOSGiMojo extends AbstractMojo {

  /**
   * Comma separated list of the id of the environments that should be processed. Default is * that means all
   * environments.
   */
  @Parameter(property = "eosgi.environmentId", defaultValue = "*")
  protected String environmentId = "*";

  private EnvironmentConfiguration[] environmentsToProcess;
  
  public EnvironmentConfiguration[] getEnvironments() {
    if ((environments == null) || (environments.length == 0)) {
        environments = new EnvironmentConfiguration[] { getDefaultEnvironment() };
    }
    return environments;
}
  
  protected EnvironmentConfiguration getDefaultEnvironment() {
    getLog().info("There is no environment specified in the project. Creating felix environment with"
            + " default settings");
    EnvironmentConfiguration defaultEnvironment = new EnvironmentConfiguration();
    defaultEnvironment.setId("equinox");
    defaultEnvironment.setFramework("equinox");
    return defaultEnvironment;
}
  
  /**
   * The environments on which the tests should run.
   */
  @Parameter
  protected EnvironmentConfiguration[] environments;
  
  
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
  
  /**
   * The Maven project.
   */
  @Parameter(property = "project")
  protected MavenProject project;
  
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
  
  @Parameter(property = "executedProject")
  protected MavenProject executedProject;
  
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
}
