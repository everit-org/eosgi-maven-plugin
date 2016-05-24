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
package org.everit.osgi.dev.maven.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.everit.osgi.dev.dist.util.configuration.schema.ArtifactType;
import org.everit.osgi.dev.dist.util.configuration.schema.ArtifactsType;
import org.everit.osgi.dev.dist.util.configuration.schema.EntryType;
import org.everit.osgi.dev.dist.util.configuration.schema.EnvironmentType;
import org.everit.osgi.dev.dist.util.configuration.schema.OSGiActionType;
import org.everit.osgi.dev.dist.util.configuration.schema.PropertiesType;

/**
 * Creates a bundle execution plan on the OSGi container based on the old and new settings and the
 * content of the bundle files.
 */
public class BundleExecutionPlan {

  /**
   * Data of bundle with the start level that is currently set with the existing configuration.
   */
  public static class BundleLocationWithCurrentStartLevel {
    public String bundleLocation;

    public int oldStartLevel;

    public BundleLocationWithCurrentStartLevel(final String bundleLocation,
        final int oldStartLevel) {
      this.bundleLocation = bundleLocation;
      this.oldStartLevel = oldStartLevel;
    }

  }

  public final Collection<String> changeStartLevelIfInitialBundleStartLevelChangesOnBundles;

  public final Map<String, Integer> higherStartLevelOnBundles;

  public final Collection<String> installBundles;

  public final Map<String, Integer> lowerStartLevelOnBundles;

  /**
   * The lowest start level that was assigned to any of the bundles that change state in the plan or
   * <code>null</code> if none of these bundles had a specific startLevel.
   */
  public final Integer lowestStartLevel;

  public final Collection<BundleLocationWithCurrentStartLevel> setInitialStartLevelOnBundles;

  public final Map<String, Integer> setStartLevelFromInitialBundles;

  public final Collection<String> startStoppedBundles;

  public final Collection<String> stopStartedBundles;

  public final Collection<String> uninstallBundles;

  public final Collection<String> updateBundles;

  /**
   * Constructor.
   *
   * @param existingDistributedEnvironment
   *          The distribution that was previously generated.
   * @param newArtifacts
   *          The artifact list of the new distribution process.
   * @param environmentRootFolder
   *          The root folder of the environment where the artifacts will be copied.
   * @param artifactResolver
   *          Resolves the artifacts.
   * @param artifactHandlerManager
   *          Artifact resolver.
   * @throws MojoExecutionException
   *           If an exception happens during generating the execution plan.
   */
  public BundleExecutionPlan(final EnvironmentType existingDistributedEnvironment,
      final ArtifactsType newArtifacts, final File environmentRootFolder,
      final PredefinedRepoArtifactResolver artifactResolver)
      throws MojoExecutionException {

    Map<String, Map<String, String>> existingBundleLocationsWithProperties =
        createExistingBundleLocationWithArtifactProperties(existingDistributedEnvironment);

    Set<String> tmpInstallBundles = new HashSet<>();

    List<String> tmpUpdateBundles = new ArrayList<>();
    List<String> tmpStopStartedBundles = new ArrayList<>();
    List<String> tmpStartStoppedBundles = new ArrayList<>();
    Map<String, Integer> tmpLowerStartLevelOnBundles = new HashMap<>();
    Map<String, Integer> tmpHigherStartLevelOnBundles = new HashMap<>();
    List<BundleLocationWithCurrentStartLevel> tmpSetInitialStartLevelOnBundles = new ArrayList<>();
    Map<String, Integer> tmpSetStartLevelFromInitialBundles = new HashMap<>();
    List<String> tmpIfInitialChanges = new ArrayList<>();

    for (ArtifactType newArtifact : newArtifacts.getArtifact()) {
      Map<String, String> newArtifactProperties = getArtifactPropertyMap(newArtifact);
      String newBundleAction = newArtifactProperties.get("bundle.action");
      if (newBundleAction != null
          && !OSGiActionType.NONE.name().equalsIgnoreCase(newBundleAction)) {
        String bundleLocation = newArtifactProperties.get("bundle.location");
        Map<String, String> existingArtifactProperties =
            existingBundleLocationsWithProperties.remove(bundleLocation);
        if (existingArtifactProperties == null) {
          if (tmpInstallBundles.contains(bundleLocation)) {
            throw new MojoExecutionException(
                "Bundle location '" + bundleLocation + "' exists twice in environment '"
                    + existingDistributedEnvironment.getId() + "'");
          }
          tmpInstallBundles.add(bundleLocation);
        } else {
          if (bundleContentChanged(newArtifact, environmentRootFolder, artifactResolver)) {
            tmpUpdateBundles.add(bundleLocation);
          } else {
            if (bundleBecameStarted(existingArtifactProperties, newArtifactProperties)) {
              tmpStartStoppedBundles.add(bundleLocation);
            }

            if (bundleBecameStopped(existingArtifactProperties, newArtifactProperties)) {
              tmpStopStartedBundles.add(bundleLocation);
            }
          }

          fillStartLevelChangeWhereNecessary(bundleData, existingBundle,
              tmpSetInitialStartLevelOnBundles, tmpSetStartLevelFromInitialBundles,
              tmpLowerStartLevelOnBundles, tmpHigherStartLevelOnBundles, tmpIfInitialChanges);
        }
      }
    }

    this.uninstallBundles = new HashSet<>(existingBundleLocationsWithProperties.keySet());
    this.installBundles = tmpInstallBundles;
    this.updateBundles = tmpUpdateBundles;
    this.startStoppedBundles = tmpStartStoppedBundles;
    this.stopStartedBundles = tmpStopStartedBundles;
    this.lowerStartLevelOnBundles = tmpLowerStartLevelOnBundles;
    this.higherStartLevelOnBundles = tmpHigherStartLevelOnBundles;
    this.setInitialStartLevelOnBundles = tmpSetInitialStartLevelOnBundles;
    this.setStartLevelFromInitialBundles = tmpSetStartLevelFromInitialBundles;
    this.changeStartLevelIfInitialBundleStartLevelChangesOnBundles = tmpIfInitialChanges;
    this.lowestStartLevel = resolveLowestStartLevel();
  }

  private boolean bundleBecameStarted(final Map<String, String> existingArtifactProperties,
      final Map<String, String> newArtifactProperties) {

    String existingBundleAction = existingArtifactProperties.get("bundle.action");
    String newBundleAction = newArtifactProperties.get("bundle.action");
    return OSGiActionType.INSTALL.name().equalsIgnoreCase(existingBundleAction)
        && OSGiActionType.START.name().equalsIgnoreCase(newBundleAction);
  }

  private boolean bundleBecameStopped(final Map<String, String> existingArtifactProperties,
      final Map<String, String> newArtifactProperties) {

    String existingBundleAction = existingArtifactProperties.get("bundle.action");
    String newBundleAction = newArtifactProperties.get("bundle.action");
    return OSGiActionType.START.name().equalsIgnoreCase(existingBundleAction)
        && OSGiActionType.INSTALL.name().equalsIgnoreCase(newBundleAction);
  }

  private boolean bundleContentChanged(final ArtifactType newArtifact,
      final File environmentRootFolder,
      final PredefinedRepoArtifactResolver artifactResolver) throws MojoExecutionException {

    Artifact resolvedArtifact =
        resolveArtifact(artifactResolver, newArtifact);
    File newArtifactFile = resolvedArtifact.getFile();
    File oldArtifactFile =
        PluginUtil.resolveArtifactAbsoluteFile(newArtifact, resolvedArtifact,
            environmentRootFolder);

    return contentDifferent(newArtifactFile, oldArtifactFile);
  }

  private boolean contentDifferent(final File newArtifactFile, final File oldArtifactFile)
      throws MojoExecutionException {
    if (!oldArtifactFile.exists()
        || (newArtifactFile.lastModified() == oldArtifactFile.lastModified()
            && newArtifactFile.length() == oldArtifactFile.length())) {
      return false;
    }
    try (InputStream newIn = new BufferedInputStream(new FileInputStream(newArtifactFile));
        InputStream oldIn = new BufferedInputStream(new FileInputStream(oldArtifactFile))) {

      int oldR = 0;
      int newR = 0;

      boolean changed = false;
      while (oldR >= 0 && newR >= 0 && !changed) {
        oldR = oldIn.read();
        newR = newIn.read();

        if (oldR != newR) {
          changed = true;
        }
      }
      return changed;
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Error during diff check on files: " + oldArtifactFile + ", " + newArtifactFile, e);
    }

  }

  private Map<String, Map<String, String>> createExistingBundleLocationWithArtifactProperties(
      final EnvironmentType existingDistributedEnvironment) {

    Map<String, Map<String, String>> result = new HashMap<>();
    if (existingDistributedEnvironment == null
        || existingDistributedEnvironment.getArtifacts() == null) {
      return result;
    }

    for (ArtifactType artifact : existingDistributedEnvironment.getArtifacts().getArtifact()) {
      Map<String, String> propertyMap = getArtifactPropertyMap(artifact);
      String bundleAction = propertyMap.get("bundle.action");
      if (bundleAction != null
          && !OSGiActionType.NONE.name().equalsIgnoreCase(bundleAction)) {

        String bundleLocation = propertyMap.get("bundle.location");
        result.put(bundleLocation, propertyMap);
      }
    }
    return result;
  }

  private void fillStartLevelChangeWhereNecessary(final BundleDataType bundleData,
      final BundleDataType existingBundle,
      final List<BundleLocationWithCurrentStartLevel> tmpSetInitialStartLevelOnBundles,
      final List<BundleDataType> tmpSetStartLevelFromInitialBundles,
      final List<BundleDataType> tmpLowerStartLevelOnBundles,
      final List<BundleDataType> tmpHigherStartLevelOnBundles,
      final List<BundleDataType> tmpIfInitialChanges) {

    Integer newStartLevel = bundleData.getStartLevel();
    Integer existingStartLevel = existingBundle.getStartLevel();
    if (!Objects.equals(newStartLevel, existingStartLevel)) {
      if (newStartLevel == null) {
        tmpSetInitialStartLevelOnBundles
            .add(new BundleLocationWithCurrentStartLevel(bundleData, existingStartLevel));
      } else if (existingStartLevel == null) {
        tmpSetStartLevelFromInitialBundles.add(bundleData);
      } else if (newStartLevel.compareTo(existingStartLevel) > 0) {
        tmpHigherStartLevelOnBundles.add(bundleData);
      } else {
        tmpLowerStartLevelOnBundles.add(bundleData);
      }
    } else if (newStartLevel == null) {
      tmpIfInitialChanges.add(bundleData);
    }
  }

  private Map<String, String> getArtifactPropertyMap(final ArtifactType artifact) {
    PropertiesType properties = artifact.getProperties();
    if (properties == null) {
      return Collections.emptyMap();
    }
    Map<String, String> result = new HashMap<>();
    List<EntryType> propertyList = properties.getProperty();
    for (EntryType propertyEntry : propertyList) {
      result.put(propertyEntry.getKey(), propertyEntry.getValue());
    }
    return result;
  }

  private Artifact resolveArtifact(final PredefinedRepoArtifactResolver artifactResolver,
      final ArtifactType artifact)
      throws MojoExecutionException {

    ArtifactRequest artifactRequest = new ArtifactRequest();
    Artifact aetherArtifact = new DefaultArtifact(artifact.getId());
    artifactRequest.setArtifact(aetherArtifact);

    Artifact resolvedArtifact = artifactResolver.resolve(artifactRequest);
    return resolvedArtifact;
  }

  private Integer resolveLowestStartLevel() {
    Integer tmpLowestStartLevel = null;
    tmpLowestStartLevel = resolveLowestStartLevel(this.installBundles, tmpLowestStartLevel);
    tmpLowestStartLevel = resolveLowestStartLevel(this.uninstallBundles, tmpLowestStartLevel);
    tmpLowestStartLevel = resolveLowestStartLevel(this.updateBundles, tmpLowestStartLevel);
    tmpLowestStartLevel = resolveLowestStartLevel(this.startStoppedBundles, tmpLowestStartLevel);
    tmpLowestStartLevel =
        resolveLowestStartLevel(this.lowerStartLevelOnBundles, tmpLowestStartLevel);

    tmpLowestStartLevel =
        resolveLowestStartLevel(this.setStartLevelFromInitialBundles, tmpLowestStartLevel);

    return tmpLowestStartLevel;
  }

  private Integer resolveLowestStartLevel(final Collection<BundleDataType> bundleDataCollection,
      final Integer pLowestStartLevel) {
    Integer tmpLowestStartLevel = pLowestStartLevel;
    for (BundleDataType bundleData : bundleDataCollection) {
      Integer startLevel = bundleData.getStartLevel();
      if (startLevel != null
          && (pLowestStartLevel == null || startLevel.compareTo(tmpLowestStartLevel) < 0)) {
        tmpLowestStartLevel = startLevel;
      }
    }
    return tmpLowestStartLevel;
  }
}
