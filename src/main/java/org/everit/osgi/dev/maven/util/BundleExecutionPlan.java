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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
   * Temporary data used for calculation.
   */
  private static class TemporaryBundleMaps {

    Map<String, Integer> higherStartLevelOnBundles = new HashMap<>();

    Map<String, Integer> ifInitialChanges = new HashMap<>();

    Map<String, Integer> installBundles = new HashMap<>();

    Map<String, Integer> lowerStartLevelOnBundles = new HashMap<>();

    Map<String, Integer> setInitialStartLevelOnBundles = new HashMap<>();

    Map<String, Integer> setStartLevelFromInitialBundles = new HashMap<>();

    Map<String, Integer> startStoppedBundles = new HashMap<>();

    Map<String, Integer> stopStartedBundles = new HashMap<>();

    Map<String, Integer> updateBundles = new HashMap<>();
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

  public final Collection<String> setInitialStartLevelOnBundles;

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
   * @throws MojoExecutionException
   *           If an exception happens during generating the execution plan.
   */
  public BundleExecutionPlan(final EnvironmentType existingDistributedEnvironment,
      final ArtifactsType newArtifacts, final File environmentRootFolder,
      final PredefinedRepoArtifactResolver artifactResolver)
      throws MojoExecutionException {

    Map<String, Map<String, String>> existingBundleLocationsWithProperties =
        createExistingBundleLocationWithArtifactProperties(existingDistributedEnvironment);

    TemporaryBundleMaps tmp = new TemporaryBundleMaps();

    for (ArtifactType newArtifact : newArtifacts.getArtifact()) {
      Map<String, String> newArtifactProperties = getArtifactPropertyMap(newArtifact);
      String newBundleAction = newArtifactProperties.get("bundle.action");
      if (newBundleAction != null
          && !OSGiActionType.NONE.name().equalsIgnoreCase(newBundleAction)) {
        String bundleLocation = newArtifactProperties.get("bundle.location");
        Integer newArtifactStartLevel = resolveStartLevel(newArtifactProperties);
        Map<String, String> existingArtifactProperties =
            existingBundleLocationsWithProperties.remove(bundleLocation);
        if (existingArtifactProperties == null) {
          if (tmp.installBundles.containsKey(bundleLocation)) {
            throw new MojoExecutionException(
                "Bundle location '" + bundleLocation + "' exists twice in environment '"
                    + existingDistributedEnvironment.getId() + "'");
          }
          tmp.installBundles.put(bundleLocation, newArtifactStartLevel);
        } else {
          if (bundleContentChanged(newArtifact, environmentRootFolder, artifactResolver)) {
            tmp.updateBundles.put(bundleLocation, newArtifactStartLevel);
          } else {
            if (bundleBecameStarted(existingArtifactProperties, newArtifactProperties)) {
              tmp.startStoppedBundles.put(bundleLocation, newArtifactStartLevel);
            } else if (bundleBecameStopped(existingArtifactProperties, newArtifactProperties)) {
              tmp.stopStartedBundles.put(bundleLocation, newArtifactStartLevel);
            }
          }

          fillStartLevelChangeWhereNecessary(bundleLocation, newArtifactProperties,
              existingArtifactProperties, tmp);
        }
      }
    }

    this.uninstallBundles = new HashSet<>(existingBundleLocationsWithProperties.keySet());
    this.installBundles = new HashSet<>(tmp.installBundles.keySet());
    this.updateBundles = new HashSet<>(tmp.updateBundles.keySet());
    this.startStoppedBundles = new HashSet<>(tmp.startStoppedBundles.keySet());
    this.stopStartedBundles = new HashSet<>(tmp.stopStartedBundles.keySet());
    this.lowerStartLevelOnBundles = tmp.lowerStartLevelOnBundles;
    this.higherStartLevelOnBundles = tmp.higherStartLevelOnBundles;
    this.setInitialStartLevelOnBundles = new HashSet<>(tmp.setInitialStartLevelOnBundles.keySet());
    this.setStartLevelFromInitialBundles = tmp.setStartLevelFromInitialBundles;
    this.changeStartLevelIfInitialBundleStartLevelChangesOnBundles =
        new HashSet<>(tmp.ifInitialChanges.keySet());

    this.lowestStartLevel = resolveLowestStartLevel(tmp);
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

  private void fillStartLevelChangeWhereNecessary(final String bundleLocation,
      final Map<String, String> newArtifactProperties,
      final Map<String, String> existingArtifactProperties,
      final TemporaryBundleMaps tmp) {

    Integer newStartLevel = resolveStartLevel(newArtifactProperties);
    Integer existingStartLevel = resolveStartLevel(existingArtifactProperties);
    if (!Objects.equals(newStartLevel, existingStartLevel)) {
      if (newStartLevel == null) {
        tmp.setInitialStartLevelOnBundles.put(bundleLocation, existingStartLevel);
      } else if (existingStartLevel == null) {
        tmp.setStartLevelFromInitialBundles.put(bundleLocation, newStartLevel);
      } else if (newStartLevel.compareTo(existingStartLevel) > 0) {
        tmp.higherStartLevelOnBundles.put(bundleLocation, newStartLevel);
      } else {
        tmp.lowerStartLevelOnBundles.put(bundleLocation, newStartLevel);
      }
    } else if (newStartLevel == null) {
      tmp.ifInitialChanges.put(bundleLocation, null);
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

  private Integer resolveLowestStartLevel(final Map<String, Integer> bundleMap,
      final Integer pLowestStartLevel) {

    Integer result = pLowestStartLevel;
    Collection<Integer> startLevels = bundleMap.values();
    for (Integer startLevel : startLevels) {
      if (startLevel != null && (result == null || startLevel.compareTo(result) < 0)) {
        result = startLevel;
      }
    }
    return result;
  }

  private Integer resolveLowestStartLevel(final TemporaryBundleMaps tmp) {
    Integer result = resolveLowestStartLevel(tmp.higherStartLevelOnBundles, null);
    result = resolveLowestStartLevel(tmp.ifInitialChanges, null);
    result = resolveLowestStartLevel(tmp.installBundles, null);
    result = resolveLowestStartLevel(tmp.lowerStartLevelOnBundles, null);
    result = resolveLowestStartLevel(tmp.setInitialStartLevelOnBundles, null);
    result = resolveLowestStartLevel(tmp.setStartLevelFromInitialBundles, null);
    result = resolveLowestStartLevel(tmp.startStoppedBundles, null);
    result = resolveLowestStartLevel(tmp.stopStartedBundles, null);
    result = resolveLowestStartLevel(tmp.updateBundles, null);
    return result;
  }

  private Integer resolveStartLevel(final Map<String, String> artifactProperties) {
    if (artifactProperties == null) {
      return null;
    }

    String startLevelString = artifactProperties.get("bundle.startLevel");
    if (startLevelString == null) {
      return null;
    }
    return Integer.parseInt(startLevelString);
  }
}
