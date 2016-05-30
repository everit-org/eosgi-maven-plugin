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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.everit.osgi.dev.dist.util.configuration.schema.OSGiActionType;

/**
 * Creates a bundle execution plan on the OSGi container based on the old and new settings and the
 * content of the bundle files.
 */
public class BundleExecutionPlan {

  /**
   * Temporary data used for calculation.
   */
  private static class TemporaryBundleMaps {

    Set<DistributableArtifact> higherStartLevelOnBundles = new HashSet<>();

    Set<DistributableArtifact> ifInitialChanges = new HashSet<>();

    Map<String, DistributableArtifact> installBundles = new HashMap<>();

    Set<DistributableArtifact> lowerStartLevelOnBundles = new HashSet<>();

    Map<DistributableArtifact, Integer> setInitialStartLevelOnBundles = new HashMap<>();

    Set<DistributableArtifact> setStartLevelFromInitialBundles = new HashSet<>();

    Set<DistributableArtifact> startStoppedBundles = new HashSet<>();

    Set<DistributableArtifact> stopStartedBundles = new HashSet<>();

    Set<DistributableArtifact> updateBundles = new HashSet<>();
  }

  public final Collection<DistributableArtifact> changeStartLevelIfInitialBundleStartLevelChangesOnBundles; // CS_DISABLE_LINE_LENGTH

  public final Collection<DistributableArtifact> higherStartLevelOnBundles;

  public final Collection<DistributableArtifact> installBundles;

  public final Collection<DistributableArtifact> lowerStartLevelOnBundles;

  /**
   * The lowest start level that was assigned to any of the bundles that change state in the plan or
   * <code>null</code> if none of these bundles had a specific startLevel.
   */
  public final Integer lowestStartLevel;

  /**
   * Map of bundle location and the old startlevel of the bundles that should have their start
   * levels reset to the initial one.
   */
  public final Map<DistributableArtifact, Integer> setInitialStartLevelOnBundles;

  public final Collection<DistributableArtifact> setStartLevelFromInitialBundles;

  public final Collection<DistributableArtifact> startStoppedBundles;

  public final Collection<DistributableArtifact> stopStartedBundles;

  public final Collection<DistributableArtifact> uninstallBundles;

  public final Collection<DistributableArtifact> updateBundles;

  /**
   * Constructor.
   *
   * @param existingArtifacts
   *          The artifacts that were existing in the environment previously.
   * @param newArtifacts
   *          The artifact list of the new distribution process.
   * @param environmentRootFolder
   *          The root folder of the environment where the artifacts will be copied.
   * @param artifactResolver
   *          Resolves the artifacts.
   * @throws MojoExecutionException
   *           If an exception happens during generating the execution plan.
   */
  public BundleExecutionPlan(final String environmentId,
      final Collection<DistributableArtifact> existingArtifacts,
      final Collection<DistributableArtifact> newArtifacts, final File environmentRootFolder,
      final PredefinedRepoArtifactResolver artifactResolver)
      throws MojoExecutionException {

    Map<String, DistributableArtifact> existingArtifactsByBundleLocation =
        createExistingBundleLocationWithArtifactProperties(existingArtifacts);

    TemporaryBundleMaps tmp = new TemporaryBundleMaps();

    for (DistributableArtifact newArtifact : newArtifacts) {
      String newBundleAction = newArtifact.properties.get("bundle.action");
      if (newBundleAction != null
          && !OSGiActionType.NONE.name().equalsIgnoreCase(newBundleAction)) {
        String bundleLocation = newArtifact.properties.get("bundle.location");
        DistributableArtifact existingArtifact =
            existingArtifactsByBundleLocation.remove(bundleLocation);
        if (existingArtifact == null) {
          if (tmp.installBundles.containsKey(bundleLocation)) {
            throw new MojoExecutionException(
                "Bundle location '" + bundleLocation + "' exists twice in environment '"
                    + environmentId + "'");
          }
          tmp.installBundles.put(bundleLocation, newArtifact);
        } else {
          if (bundleContentChanged(newArtifact, environmentRootFolder, artifactResolver)) {
            tmp.updateBundles.add(newArtifact);
          } else {
            if (bundleBecameStarted(existingArtifact.properties, newArtifact.properties)) {
              tmp.startStoppedBundles.add(newArtifact);
            } else if (bundleBecameStopped(existingArtifact.properties, newArtifact.properties)) {
              tmp.stopStartedBundles.add(newArtifact);
            }
          }

          fillStartLevelChangeWhereNecessary(newArtifact, existingArtifact, tmp);
        }
      }
    }

    this.uninstallBundles = new HashSet<>(existingArtifactsByBundleLocation.values());
    this.installBundles = new HashSet<>(tmp.installBundles.values());
    this.updateBundles = tmp.updateBundles;
    this.startStoppedBundles = tmp.startStoppedBundles;
    this.stopStartedBundles = tmp.stopStartedBundles;
    this.lowerStartLevelOnBundles = tmp.lowerStartLevelOnBundles;
    this.higherStartLevelOnBundles = tmp.higherStartLevelOnBundles;
    this.setInitialStartLevelOnBundles = tmp.setInitialStartLevelOnBundles;
    this.setStartLevelFromInitialBundles = tmp.setStartLevelFromInitialBundles;
    this.changeStartLevelIfInitialBundleStartLevelChangesOnBundles = tmp.ifInitialChanges;

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

  private boolean bundleContentChanged(final DistributableArtifact newArtifact,
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

  private Map<String, DistributableArtifact> createExistingBundleLocationWithArtifactProperties(
      final Collection<DistributableArtifact> existingArtifacts) {

    Map<String, DistributableArtifact> result = new HashMap<>();
    if (existingArtifacts == null) {
      return result;
    }

    for (DistributableArtifact artifact : existingArtifacts) {

      String bundleAction = artifact.properties.get("bundle.action");
      if (bundleAction != null
          && !OSGiActionType.NONE.name().equalsIgnoreCase(bundleAction)) {

        String bundleLocation = artifact.properties.get("bundle.location");
        result.put(bundleLocation, artifact);
      }
    }
    return result;
  }

  private void fillStartLevelChangeWhereNecessary(
      final DistributableArtifact newArtifact,
      final DistributableArtifact existingArtifact,
      final TemporaryBundleMaps tmp) {

    Integer newStartLevel = resolveStartLevel(newArtifact.properties);
    Integer existingStartLevel = resolveStartLevel(existingArtifact.properties);
    if (!Objects.equals(newStartLevel, existingStartLevel)) {
      if (newStartLevel == null) {
        tmp.setInitialStartLevelOnBundles.put(newArtifact, existingStartLevel);
      } else if (existingStartLevel == null) {
        tmp.setStartLevelFromInitialBundles.add(newArtifact);
      } else if (newStartLevel.compareTo(existingStartLevel) > 0) {
        tmp.higherStartLevelOnBundles.add(newArtifact);
      } else {
        tmp.lowerStartLevelOnBundles.add(newArtifact);
      }
    } else if (newStartLevel == null) {
      tmp.ifInitialChanges.add(newArtifact);
    }
  }

  private Artifact resolveArtifact(final PredefinedRepoArtifactResolver artifactResolver,
      final DistributableArtifact artifact)
      throws MojoExecutionException {

    ArtifactRequest artifactRequest = new ArtifactRequest();
    Artifact aetherArtifact = new DefaultArtifact(artifact.gav);
    artifactRequest.setArtifact(aetherArtifact);

    Artifact resolvedArtifact = artifactResolver.resolve(artifactRequest);
    return resolvedArtifact;
  }

  private Integer resolveLowestStartLevel(final Collection<DistributableArtifact> artifacts,
      final Integer pLowestStartLevel) {

    Integer result = pLowestStartLevel;
    for (DistributableArtifact artifact : artifacts) {
      Integer startLevel = resolveStartLevel(artifact.properties);
      if (startLevel != null && (result == null || startLevel.compareTo(result) < 0)) {
        result = startLevel;
      }
    }
    return result;
  }

  private Integer resolveLowestStartLevel(final TemporaryBundleMaps tmp) {
    Integer result = resolveLowestStartLevel(tmp.higherStartLevelOnBundles, null);
    result = resolveLowestStartLevel(tmp.ifInitialChanges, null);
    result = resolveLowestStartLevel(tmp.installBundles.values(), null);
    result = resolveLowestStartLevel(tmp.lowerStartLevelOnBundles, null);
    result = resolveLowestStartLevel(tmp.setInitialStartLevelOnBundles.keySet(), null);
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
